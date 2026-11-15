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

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 多进程管理器（FR-WD-01/07）：按配置托管一组服务，负责判活、自动拉起、重启限流与人工控制。
 *
 * <p>由 systemd 只引导本进程一次，其余服务（后端、可选工作进程）一律由这里以子进程方式拉起，
 * 保证全局只有一套监督逻辑；不引入 pm2 等第二套守护，避免互相踩。</p>
 *
 * @author jdrk
 * @since 2026/11/5
 */
final class ProcessManager implements ManagerEventSink {

    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, ManagedService> services = new LinkedHashMap<>();

    private final Path dir;

    private final Path logFile;

    private final Path journalFile;

    private final int checkIntervalSeconds;

    private volatile boolean running = true;

    /** 控制面动作线程池：启动要等最多 180 秒心跳，不能占住 HTTP 线程 */
    private final ExecutorService controlPool = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "jdrk-pm-control");
        thread.setDaemon(true);
        return thread;
    });

    ProcessManager(List<ServiceSpec> specs, Path dir, Path logFile, int checkIntervalSeconds) {
        this.dir = dir.toAbsolutePath().normalize();
        this.logFile = logFile.toAbsolutePath().normalize();
        this.journalFile = this.dir.resolve("restarts.jsonl");
        this.checkIntervalSeconds = Math.max(5, checkIntervalSeconds);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        for (ServiceSpec spec : specs) {
            this.services.put(spec.name, new ManagedService(spec, client));
        }
    }

    Collection<ManagedService> services() {
        return this.services.values();
    }

    ManagedService service(String name) {
        return this.services.get(name);
    }

    /**
     * 启动全部自启服务并进入监督循环（阻塞）
     */
    void run() {
        try {
            Files.createDirectories(this.dir);
            if (this.logFile.getParent() != null) {
                Files.createDirectories(this.logFile.getParent());
            }
        } catch (IOException e) {
            System.out.println("初始化运行时目录失败：" + e.getMessage());
        }
        this.log("多进程管理器启动：托管 " + this.services.size() + " 个服务，检测间隔 " + this.checkIntervalSeconds
            + " 秒");
        for (ManagedService service : this.services.values()) {
            if (service.autostart()) {
                service.start(this);
            }
        }
        while (this.running) {
            try {
                this.tick();
            } catch (Exception e) {
                this.log("监督循环异常：" + e.getMessage());
            }
            try {
                Thread.sleep(Duration.ofSeconds(this.checkIntervalSeconds).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 执行一个监督周期
     */
    synchronized void tick() {
        for (ManagedService service : this.services.values()) {
            try {
                service.tick(this);
            } catch (Exception e) {
                this.log(service.name() + " 监督异常：" + e.getMessage());
            }
        }
    }

    /**
     * 请求退出（systemd 停止时调用）
     */
    void shutdown() {
        this.running = false;
        this.controlPool.shutdownNow();
        for (ManagedService service : this.services.values()) {
            service.stop(this);
        }
    }

    /**
     * 控制面：提交启动（异步，立即返回）
     *
     * <p>不能同步执行：启动要等后端心跳就绪（最长 start.timeout.seconds），
     * 同步会把 HTTP 请求挂住到超时，运维以为没生效又点一次，反而触发重复拉起。</p>
     *
     * @param name 服务名
     * @return 服务是否存在
     */
    boolean submitStart(String name) {
        ManagedService service = this.services.get(name);
        if (service == null) {
            return false;
        }
        this.controlPool.submit(() -> service.start(this));
        return true;
    }

    /**
     * 控制面：提交停止（异步，立即返回）
     *
     * @param name 服务名
     * @return 服务是否存在
     */
    boolean submitStop(String name) {
        ManagedService service = this.services.get(name);
        if (service == null) {
            return false;
        }
        this.controlPool.submit(() -> service.stop(this));
        return true;
    }

    /**
     * 控制面：提交重启（异步，立即返回）
     *
     * @param name 服务名
     * @return 服务是否存在
     */
    boolean submitRestart(String name) {
        ManagedService service = this.services.get(name);
        if (service == null) {
            return false;
        }
        this.controlPool.submit(() -> service.restart(this));
        return true;
    }

    /**
     * 控制面：停止指定服务
     *
     * @param name 服务名
     * @return 是否存在该服务
     */
    boolean stop(String name) {
        ManagedService service = this.services.get(name);
        if (service == null) {
            return false;
        }
        service.stop(this);
        return true;
    }

    /**
     * 控制面：重启指定服务
     *
     * @param name 服务名
     * @return 是否成功
     */
    boolean restart(String name) {
        ManagedService service = this.services.get(name);
        return service != null && service.restart(this);
    }

    /**
     * 控制面：状态快照
     *
     * @return 有序映射，可直接序列化为 JSON
     */
    Map<String, Object> status() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("manager", "jdrk-watchdog");
        root.put("pid", ProcessHandle.current().pid());
        root.put("time", LocalDateTime.now().format(STAMP));
        root.put("checkIntervalSeconds", this.checkIntervalSeconds);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ManagedService service : this.services.values()) {
            items.add(service.snapshot());
        }
        root.put("services", items);
        return root;
    }

    @Override
    public void log(String message) {
        String line = LocalDateTime.now().format(STAMP) + " " + message;
        System.out.println(line);
        try {
            if (this.logFile.getParent() != null) {
                Files.createDirectories(this.logFile.getParent());
            }
            Files.writeString(this.logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("写管理器日志失败：" + e.getMessage());
        }
    }

    @Override
    public void record(String service, String eventType, String reason, Long pid,
        int restartCount) {
        // 服务名放在 reason 前缀里而不是新增字段：后端 WatchdogService.ingest() 按既有字段解析，
        // 多出来的键会让整行被判为失败并永久留在文件里重试。
        String detail = "[" + service + "] " + (reason == null ? "" : reason);
        String json =
            "{\"time\":\"" + LocalDateTime.now().format(STAMP) + "\",\"eventType\":\"" + eventType
                + "\",\"reason\":\"" + this.escape(detail) + "\",\"restartCount\":" + restartCount
                + ",\"pid\":"
                + (pid == null || pid <= 0 ? "null" : pid) + "}";
        try {
            Files.createDirectories(this.dir);
            Files.writeString(this.journalFile, json + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            this.log("写重启事件失败：" + e.getMessage());
        }
        this.log("[" + eventType + "] " + detail);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
