/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.watchdog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 单个被托管服务的生命周期（FR-WD-01/02/03/04）。
 *
 * <p>状态机：{@code STOPPED → STARTING → RUNNING ⇄ DEGRADED → RESTART_LIMIT}。
 * 判活有两条路：<b>心跳</b>（配置了 heartbeat.url 就以前端心跳为准）与<b>进程存在性</b>。
 * 只有进程、没有心跳的服务（command 类型）以进程存活为唯一判据。</p>
 *
 * @author jdrk
 * @since 2026/11/5
 */
final class ManagedService {

    /** 未启动（初始或人工停止） */
    static final String STOPPED = "STOPPED";
    /** 已拉起、等待首次心跳 */
    static final String STARTING = "STARTING";
    /** 心跳正常且进程存活 */
    static final String RUNNING = "RUNNING";
    /** 进程在但心跳超时（疑似假死） */
    static final String DEGRADED = "DEGRADED";
    /** 触发重启限流，已停止自动拉起 */
    static final String RESTART_LIMIT = "RESTART_LIMIT";
    /** 拉起失败 */
    static final String FAILED = "FAILED";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ServiceSpec spec;

    private final HttpClient client;

    private final Deque<LocalDateTime> restarts = new ArrayDeque<>();

    private Process process;

    private Long observedPid;

    private LocalDateTime lastGoodHeartbeat;

    private LocalDateTime startedAt;

    private int restartCount;

    private boolean limited;

    private boolean operatorStopped = true;

    private String state = STOPPED;

    private String lastError;

    ManagedService(ServiceSpec spec, HttpClient client) {
        this.spec = spec;
        this.client = client;
    }

    String name() {
        return this.spec.name;
    }

    String state() {
        return this.state;
    }

    int restartCount() {
        return this.restartCount;
    }

    boolean autostart() {
        return this.spec.autostart;
    }

    /**
     * 监督一个周期：判活 → 必要时重启
     *
     * @param sink 事件接收器（写日志与重启事件）
     */
    synchronized void tick(ManagerEventSink sink) {
        if (this.operatorStopped) {
            this.state = STOPPED;
            return;
        }
        if (STARTING.equals(this.state) && this.alive()) {
            // 启动窗口内由 start() 自己的等待逻辑负责，这里不得再用「心跳超时」判假死，
            // 否则控制面重启与监督循环并发时，刚拉起的进程会被立刻误杀。
            return;
        }
        WatchdogMain.Heartbeat heartbeat = this.probe();
        if (heartbeat != null) {
            if (this.lastGoodHeartbeat == null) {
                sink.log(this.spec.name + " 心跳恢复（pid=" + heartbeat.pid + "，运行 " + heartbeat.uptimeMs
                    + "ms）");
            }
            this.observedPid = heartbeat.pid;
            this.lastGoodHeartbeat = LocalDateTime.now();
            this.limited = false;
            this.state = RUNNING;
            return;
        }
        boolean alive = this.alive();
        if (this.spec.heartbeatUrl.isBlank()) {
            // 无心跳配置：进程存活即视为健康
            this.state = alive ? RUNNING : FAILED;
            if (!alive) {
                this.handleMissing(sink, "进程不存在");
            }
            return;
        }
        // 基准必须回落到「拉起时间」：首次心跳还没到时用 Long.MAX_VALUE 会让任何存活进程
        // 立刻被判定为假死（实测：控制面重启后 8 秒内被连杀两次）。
        LocalDateTime base =
            this.lastGoodHeartbeat != null ? this.lastGoodHeartbeat : this.startedAt;
        long silence =
            base == null ? -1L : Duration.between(base, LocalDateTime.now()).getSeconds();
        if (!alive) {
            this.handleMissing(sink, "进程不存在");
            return;
        }
        if (silence > this.spec.heartbeatTimeoutSeconds) {
            this.state = DEGRADED;
            this.handleHang(sink, silence);
        }
    }

    private void handleMissing(ManagerEventSink sink, String reason) {
        if (!this.allowRestart()) {
            this.markLimited(sink, reason);
            return;
        }
        this.kill(this.observedPid);
        sink.record(this.spec.name, WatchdogMain.EVENT_CRASH, reason, this.observedPid,
            this.restartCount);
        this.launch(reason, sink);
    }

    private String hangReason(long silence) {
        return silence < 0 ? "从未收到心跳（心跳文件缺失）" : "心跳超时（假死）已达 " + silence + " 秒";
    }

    private void handleHang(ManagerEventSink sink, long silence) {
        if (!this.allowRestart()) {
            this.markLimited(sink, hangReason(silence));
            return;
        }
        String reason = hangReason(silence);
        this.kill(this.observedPid);
        sink.record(this.spec.name, WatchdogMain.EVENT_TIMEOUT, reason, this.observedPid,
            this.restartCount);
        this.launch(reason, sink);
    }

    private void markLimited(ManagerEventSink sink, String reason) {
        if (!this.limited) {
            sink.record(this.spec.name, WatchdogMain.EVENT_LIMIT,
                "一小时内重启已达上限 " + this.spec.restartLimitPerHour
                    + " 次，停止自动拉起：" + reason,
                this.observedPid, this.restartCount);
        }
        this.limited = true;
        this.state = RESTART_LIMIT;
    }

    /**
     * 人工启动
     *
     * @param sink 事件接收器
     * @return 是否启动成功（仅表示进程已拉起，不代表心跳已就绪）
     */
    synchronized boolean start(ManagerEventSink sink) {
        this.operatorStopped = false;
        this.limited = false;
        // 人工操作是运维的显式覆盖：清空限流窗口，允许立刻再拉一次
        this.restarts.clear();
        return this.launch("人工启动", sink);
    }

    /**
     * 人工停止
     *
     * @param sink 事件接收器
     */
    synchronized void stop(ManagerEventSink sink) {
        this.operatorStopped = true;
        this.kill(this.observedPid);
        this.state = STOPPED;
        this.lastGoodHeartbeat = null;
        sink.log(this.spec.name + " 已停止");
    }

    /**
     * 人工重启：先停后起
     *
     * @param sink 事件接收器
     * @return 是否成功拉起
     */
    synchronized boolean restart(ManagerEventSink sink) {
        this.kill(this.observedPid);
        this.operatorStopped = false;
        this.limited = false;
        this.restarts.clear();
        return this.launch("人工重启", sink);
    }

    /**
     * 真正执行一次拉起（内部方法，与对外的 {@link #start(ManagerEventSink)} 区分开：对外是运维动作，要清限流窗口，
     * 对内是监督循环的诊断结论，必须老老实实计入限流）
     *
     * @param reason 拉起原因（写日志用）
     * @param sink 事件接收器
     * @return 是否已成功拉起进程
     */
    private boolean launch(String reason, ManagerEventSink sink) {
        // 启动尝试也要计入限流：否则二进制缺失/权限不足这类「必然失败」的启动会每轮重试，
        // 把 CPU 与日志打满（实测：每 5 秒一条拉起失败，永不停）。
        if (!this.allowRestart()) {
            this.markLimited(sink, "一小时内启动尝试已达上限 " + this.spec.restartLimitPerHour + " 次");
            return false;
        }
        this.restarts.addLast(LocalDateTime.now());
        this.restartCount++;
        try {
            if (this.alive()) {
                sink.log(this.spec.name + " 已在运行（pid=" + this.observedPid + "），跳过重复拉起");
                return true;
            }
            if (ServiceSpec.TYPE_JAVA.equals(this.spec.type)
                && !Files.isRegularFile(Paths.get(this.spec.jar))) {
                this.state = FAILED;
                this.lastError = "找不到应用包 " + this.spec.jar;
                sink.log(this.spec.name + " 启动失败：" + this.lastError);
                return false;
            }
            ProcessBuilder builder = new ProcessBuilder(this.spec.buildCommand());
            builder.directory(this.spec.dir.toFile());
            Path log = this.spec.logFile;
            if (log.getParent() != null) {
                Files.createDirectories(log.getParent());
            }
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            this.process = builder.start();
            this.observedPid = this.process.pid();
            this.startedAt = LocalDateTime.now();
            this.state = STARTING;
            this.lastError = null;
            sink.log(this.spec.name + " 已拉起（原因：" + reason + "，pid=" + this.process.pid() + "，累计重启 "
                + this.restartCount + " 次）");
            boolean healthy = this.waitForHealth();
            if (healthy) {
                this.state = RUNNING;
                sink.log(
                    this.spec.name + " 启动成功" + (this.spec.heartbeatUrl.isBlank() ? "" : "，心跳已恢复"));
            } else {
                sink.log(this.spec.name + " 启动后 " + this.spec.startTimeoutSeconds
                    + " 秒内仍未就绪，将在下一轮继续处理");
            }
            return true;
        } catch (IOException e) {
            this.state = FAILED;
            this.lastError = e.getMessage();
            sink.log(this.spec.name + " 拉起失败：" + e.getMessage());
            return false;
        }
    }

    private boolean waitForHealth() {
        long deadline = System.currentTimeMillis() + this.spec.startTimeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            // 缺陷 78：启动窗口内子进程若已退出必须立即返回，否则会阻塞到 startTimeout
            if (this.process != null && !this.process.isAlive()) {
                return false;
            }
            if (this.spec.heartbeatUrl.isBlank()) {
                return this.alive();
            }
            if (this.probe() != null) {
                this.lastGoodHeartbeat = LocalDateTime.now();
                return true;
            }
        }
        return false;
    }

    private boolean allowRestart() {
        LocalDateTime hourAgo = LocalDateTime.now().minusHours(1);
        while (!this.restarts.isEmpty() && this.restarts.peekFirst().isBefore(hourAgo)) {
            this.restarts.pollFirst();
        }
        return this.restarts.size() < this.spec.restartLimitPerHour;
    }

    /**
     * 探测心跳
     *
     * @return 心跳；不可达或非 200 返回 null
     */
    WatchdogMain.Heartbeat probe() {
        if (this.spec.heartbeatUrl.isBlank()) {
            return null;
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(this.spec.heartbeatUrl))
                .timeout(Duration.ofSeconds(10)).GET();
            if (!this.spec.heartbeatToken.isBlank()) {
                builder.header("X-Watchdog-Token", this.spec.heartbeatToken);
            }
            HttpResponse<String> response =
                this.client.send(builder.build(), HttpResponse.BodyHandlers
                    .ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode node = root != null && root.has("data") ? root.get("data") : root;
            return MAPPER.treeToValue(node, WatchdogMain.Heartbeat.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean alive() {
        if (this.process != null && this.process.isAlive()) {
            this.observedPid = this.process.pid();
            return true;
        }
        Long pid = this.observedPid;
        return pid != null && pid > 0
            && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private void kill(Long pid) {
        try {
            if (this.process != null && this.process.isAlive()) {
                this.process.destroy();
                if (!this.process.waitFor(20, TimeUnit.SECONDS)) {
                    this.process.destroyForcibly();
                }
            } else if (pid != null && pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive)
                .orElse(false)) {
                // 缺陷 79：进程已不存在时不得再尝试结束
                this.killByPid(pid);
            }
        } catch (Exception e) {
            this.lastError = "结束进程失败：" + e.getMessage();
        } finally {
            this.process = null;
            this.observedPid = null;
            this.lastGoodHeartbeat = null;
        }
    }

    private boolean killByPid(long pid) {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isPresent()) {
            ProcessHandle target = handle.get();
            try {
                target.destroy();
            } catch (Exception e) {
                this.lastError = "发送结束信号失败（pid=" + pid + "）：" + e.getMessage();
            }
            if (this.waitGone(target, 20_000L)) {
                return true;
            }
            try {
                target.destroyForcibly();
            } catch (Exception e) {
                this.lastError = "强制结束失败（pid=" + pid + "）：" + e.getMessage();
            }
            if (this.waitGone(target, 10_000L)) {
                return true;
            }
        }
        return this.killByCommand(pid);
    }

    private boolean waitGone(ProcessHandle handle, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (!handle.isAlive()) {
                return true;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return !handle.isAlive();
            }
        }
        return !handle.isAlive();
    }

    private boolean killByCommand(long pid) {
        for (java.util.List<String> command : java.util.List
            .of(java.util.List.of("taskkill", "/PID", String
                .valueOf(pid), "/F", "/T"), java.util.List.of("kill", "-9", String.valueOf(pid)))) {
            try {
                Process killer = new ProcessBuilder(command).inheritIO().start();
                if (killer.waitFor(20, TimeUnit.SECONDS) && killer.exitValue() == 0) {
                    return true;
                }
            } catch (Exception e) {
                // 该平台没有这个命令，继续尝试下一个
            }
        }
        return false;
    }

    /**
     * 供控制面查询的快照
     *
     * @return 有序映射
     */
    Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", this.spec.name);
        map.put("type", this.spec.type);
        map.put("state", this.state);
        map.put("pid", this.alive() ? this.observedPid : null);
        map.put("restartCount", this.restartCount);
        map.put("restartLimitPerHour", this.spec.restartLimitPerHour);
        map.put("restartsLastHour", this.restarts.size());
        map.put("limited", this.limited);
        map.put("autostart", this.spec.autostart);
        map.put("critical", this.spec.critical);
        map.put("startedAt", this.startedAt == null ? null : this.startedAt.toString());
        map.put("lastHeartbeat",
            this.lastGoodHeartbeat == null ? null : this.lastGoodHeartbeat.toString());
        map.put("heartbeatUrl", this.spec.heartbeatUrl.isBlank() ? null : this.spec.heartbeatUrl);
        map.put("logFile", this.spec.logFile.toString());
        map.put("lastError", this.lastError);
        return map;
    }
}
