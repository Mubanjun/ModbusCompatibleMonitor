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

package top.continew.admin.monitor.runtime.watchdog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizWatchdogLogMapper;
import top.continew.admin.monitor.model.entity.BizWatchdogLogDO;
import top.continew.admin.monitor.runtime.cluster.ClusterLeaderService;
import top.continew.admin.monitor.runtime.cluster.ClusterProperties;
import top.continew.admin.monitor.runtime.model.HeartbeatResp;
import top.continew.admin.monitor.runtime.model.RestartEventResp;
import top.continew.admin.monitor.runtime.model.SelfCheckItemResp;
import top.continew.admin.monitor.runtime.model.WatchdogStatusResp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 看门狗后端侧（FR-WD-02/03/05/06）。
 *
 * <p>后端周期性把心跳写入 `watchdog/heartbeat.json` 并对外暴露心跳接口，独立看门狗进程据此
 * 判定「进程不在」或「假死」；重启与限流事件由看门狗写入 `watchdog/restarts.jsonl`，
 * 这里负责落库到 `biz_watchdog_log` 供页面与自检使用。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchdogService implements ApplicationListener<ApplicationReadyEvent> {

    /** 触发重启限流的事件类型 */
    private static final List<String> RESTART_EVENT_TYPES = List.of("CRASH", "HEARTBEAT_TIMEOUT",
        "RESTART", "REBOOT_LIMIT");

    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 重启事件文件 */
    private static final String RESTART_FILE = "restarts.jsonl";

    /** 心跳文件 */
    private static final String HEARTBEAT_FILE = "heartbeat.json";

    private final WatchdogProperties properties;
    private final ClusterProperties clusterProperties;
    private final ClusterLeaderService clusterLeaderService;
    private final WatchdogSelfCheckService selfCheckService;
    private final BizWatchdogLogMapper watchdogLogMapper;
    private final ObjectMapper objectMapper;

    private final long startedAt = System.currentTimeMillis();

    private ScheduledExecutorService heartbeatWriter;

    /**
     * 启动周期心跳
     */
    @PostConstruct
    public void start() {
        long interval = Math.max(5, properties.getHeartbeatIntervalSeconds());
        heartbeatWriter = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "jdrk-watchdog-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatWriter.scheduleWithFixedDelay(this::writeHeartbeatQuietly, 2, interval,
            TimeUnit.SECONDS);
        log.info("看门狗心跳已启动：每 {} 秒写入 {}（超时阈值 {} 秒）", interval, this.heartbeatFile(),
            properties.getHeartbeatTimeoutSeconds());
    }

    /**
     * 应用就绪后执行一次完整自检（FR-WD-06）
     *
     * <p>必须等应用就绪再自检：在 {@code @PostConstruct} 阶段采集调度尚未启动，
     * 会把「采集调度」误报为未通过。</p>
     *
     * @param event 就绪事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.isSelfCheckOnStartup() || heartbeatWriter == null) {
            return;
        }
        // 延后执行：采集引擎同样监听就绪事件，立即自检会把「采集调度」误报为未通过
        heartbeatWriter.schedule(() -> {
            try {
                List<SelfCheckItemResp> items = this.selfCheck();
                log.info("启动自检完成：{}", this.summary(items));
            } catch (Exception e) {
                log.warn("启动自检失败：{}", e.getMessage());
            }
        }, 20, TimeUnit.SECONDS);
    }

    /**
     * 停止心跳
     */
    @PreDestroy
    public void stop() {
        ScheduledExecutorService local = heartbeatWriter;
        heartbeatWriter = null;
        if (local != null) {
            local.shutdownNow();
        }
    }

    /**
     * 心跳（FR-WD-02）：轻量检查 + 落心跳文件
     *
     * @return 心跳内容
     */
    public HeartbeatResp heartbeat() {
        HeartbeatResp resp = new HeartbeatResp();
        resp.setProcessName(properties.getProcessName());
        resp.setPid(ProcessHandle.current().pid());
        resp.setTimestamp(LocalDateTime.now().format(STAMP));
        resp.setUptimeMs(System.currentTimeMillis() - startedAt);
        resp.setChecks(selfCheckService.lightChecks());
        resp.setHealthy(
            resp.getChecks().stream().allMatch(item -> Boolean.TRUE.equals(item.getOk())));
        this.writeHeartbeat(resp, true);
        this.ingestQuietly();
        return resp;
    }

    /**
     * 完整自检（FR-WD-06）：数据源、采集、串口、暂存、上报、同步
     *
     * @return 检查项
     */
    public List<SelfCheckItemResp> selfCheck() {
        List<SelfCheckItemResp> items = selfCheckService.selfCheck(Paths.get(properties.getDir()),
            this.logDirectory());
        this.recordSelfCheck(items);
        return items;
    }

    /**
     * 看门狗状态（FR-WD-02/05/06）
     *
     * @return 状态
     */
    public WatchdogStatusResp status() {
        WatchdogStatusResp resp = new WatchdogStatusResp();
        resp.setProcessName(properties.getProcessName());
        resp.setHeartbeatFile(this.heartbeatFile().toString());
        resp.setWatchdogLog(Paths.get(properties.getWatchdogLog()).toAbsolutePath().normalize()
            .toString());
        resp.setHeartbeatTimeoutSeconds(properties.getHeartbeatTimeoutSeconds());
        // 集群角色（NFR-SCALE-04）：单机模式为 SINGLE，多实例模式下为 LEADER/FOLLOWER
        resp.setClusterEnabled(clusterProperties.isEnabled());
        resp.setClusterRole(clusterLeaderService.role());
        resp.setClusterInstanceId(clusterLeaderService.instanceId());
        resp.setClusterLeaseKey(clusterProperties.getLockKey());
        resp.setClusterLastError(clusterLeaderService.lastError());
        this.fillHeartbeat(resp);
        this.fillRestarts(resp);
        this.fillSelfCheck(resp);
        resp.setLogTail(this.tail(this.heartbeatLogPath(), 40));
        return resp;
    }

    /**
     * 把看门狗写入的重启事件落库（FR-WD-03）
     *
     * @return 本次落库条数
     */
    public int ingest() {
        Path file = this.restartFile();
        if (!Files.isRegularFile(file)) {
            return 0;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("读取重启事件失败：{}", e.getMessage());
            return 0;
        }
        int count = 0;
        List<String> failed = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                RestartEventResp event = objectMapper.readValue(line, RestartEventResp.class);
                boolean saved = this.saveLog(event.getEventType() == null ? "RESTART" : event
                    .getEventType(), event.getReason(), event.getRestartCount());
                if (saved) {
                    count++;
                } else {
                    failed.add(line);
                }
            } catch (Exception e) {
                failed.add(line);
                log.warn("解析重启事件失败：{}", e.getMessage(), e);
            }
        }
        try {
            if (failed.isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                // 落库失败的事件必须留在文件里等下一轮心跳重试：
                // 数据库抖动一次就永久丢事件，是运维最不能接受的失败方式
                Files.write(file, failed, StandardCharsets.UTF_8);
                log.warn("有 {} 条看门狗事件未落库，已保留待重试", failed.size());
            }
        } catch (IOException e) {
            log.warn("清理重启事件文件失败：{}", e.getMessage(), e);
        }
        if (count > 0) {
            log.info("已落库 {} 条看门狗事件", count);
        }
        return count;
    }

    /**
     * 看门狗日志所在目录
     *
     * @return 目录
     */
    private Path logDirectory() {
        Path log = Paths.get(properties.getWatchdogLog()).toAbsolutePath().normalize();
        return log.getParent() == null ? Paths.get(".").toAbsolutePath().normalize()
            : log.getParent();
    }

    private void recordSelfCheck(List<SelfCheckItemResp> items) {
        this.saveLog("SELF_CHECK", this.summary(items), this.currentRestartCount());
    }

    private boolean saveLog(String eventType, String detail, Integer restartCount) {
        try {
            BizWatchdogLogDO entity = new BizWatchdogLogDO();
            // 事件多由心跳线程（无登录用户）写入，create_user 是 NOT NULL 列：
            // 与告警/同步/上报等运行时写入保持一致，统一记为系统用户 0
            entity.setCreateUser(0L);
            entity.setProcessName(properties.getProcessName());
            entity.setEventType(eventType);
            entity.setEventTime(LocalDateTime.now());
            entity
                .setDetail(detail == null ? null : detail.length() > 990 ? detail.substring(0, 990)
                    : detail);
            entity.setRestartCount(restartCount);
            watchdogLogMapper.insert(entity);
            return true;
        } catch (Exception e) {
            // 带上堆栈：只打 e.getMessage() 时，NPE 之类的异常会留下一条没有内容的告警
            log.warn("写看门狗日志失败（{}）：{}", eventType, e.getMessage(), e);
            return false;
        }
    }

    private String summary(List<SelfCheckItemResp> items) {
        long failed = items.stream().filter(item -> !Boolean.TRUE.equals(item.getOk())).count();
        String detail = items.stream().map(item -> item.getName() + "=" + (Boolean.TRUE.equals(item
            .getOk()) ? "OK" : "FAIL")).reduce((left, right) -> left + "," + right).orElse("");
        return "失败 " + failed + " 项：" + detail;
    }

    private void writeHeartbeatQuietly() {
        try {
            HeartbeatResp resp = new HeartbeatResp();
            resp.setProcessName(properties.getProcessName());
            resp.setPid(ProcessHandle.current().pid());
            resp.setTimestamp(LocalDateTime.now().format(STAMP));
            resp.setUptimeMs(System.currentTimeMillis() - startedAt);
            resp.setChecks(selfCheckService.lightChecks());
            resp.setHealthy(
                resp.getChecks().stream().allMatch(item -> Boolean.TRUE.equals(item.getOk())));
            this.writeHeartbeat(resp, false);
            this.ingestQuietly();
        } catch (Exception e) {
            log.debug("写心跳失败：{}", e.getMessage());
        }
    }

    private void writeHeartbeat(HeartbeatResp resp, boolean logIt) {
        Path file = this.heartbeatFile();
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("processName", resp.getProcessName());
            payload.put("pid", resp.getPid());
            payload.put("timestamp", resp.getTimestamp());
            payload.put("uptimeMs", resp.getUptimeMs());
            payload.put("healthy", resp.getHealthy());
            payload.put("checks", resp.getChecks());
            Files.writeString(file, objectMapper.writeValueAsString(payload),
                StandardCharsets.UTF_8);
            if (logIt) {
                log.debug("心跳已写入：{} healthy={}", file, resp.getHealthy());
            }
        } catch (IOException e) {
            log.warn("写心跳文件失败：{}", e.getMessage());
        }
    }

    private void ingestQuietly() {
        try {
            this.ingest();
        } catch (Exception e) {
            log.debug("落库看门狗事件失败：{}", e.getMessage());
        }
    }

    private void fillHeartbeat(WatchdogStatusResp resp) {
        Path file = this.heartbeatFile();
        if (!Files.isRegularFile(file)) {
            resp.setHeartbeatStale(true);
            resp.setHeartbeatMessage("心跳文件缺失");
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Map<String, Object> payload = objectMapper.readValue(in, new TypeReference<>() {
            });
            Object timestamp = payload.get("timestamp");
            resp.setLastHeartbeat(timestamp == null ? null : String.valueOf(timestamp));
            LocalDateTime time =
                timestamp == null ? null : LocalDateTime.parse(String.valueOf(timestamp),
                    STAMP);
            if (time == null) {
                resp.setHeartbeatAgeSeconds(null);
                resp.setHeartbeatStale(true);
                resp.setHeartbeatMessage("从未收到心跳");
                return;
            }
            long age = Duration.between(time, LocalDateTime.now()).getSeconds();
            resp.setHeartbeatAgeSeconds(age);
            resp.setHeartbeatStale(age > properties.getHeartbeatTimeoutSeconds());
            resp.setHeartbeatMessage("心跳正常");
        } catch (Exception e) {
            resp.setHeartbeatStale(true);
            resp.setHeartbeatMessage("心跳文件读取失败");
            log.debug("读取心跳文件失败：{}", e.getMessage());
        }
    }

    private void fillRestarts(WatchdogStatusResp resp) {
        List<BizWatchdogLogDO> rows = watchdogLogMapper.lambdaQuery()
            .in(BizWatchdogLogDO::getEventType,
                RESTART_EVENT_TYPES)
            .orderByDesc(BizWatchdogLogDO::getEventTime).last("LIMIT " + Math.max(1,
                properties.getRestartHistoryLimit()))
            .list();
        List<RestartEventResp> events = new ArrayList<>();
        LocalDateTime hourAgo = LocalDateTime.now().minusHours(1);
        int lastHour = 0;
        for (BizWatchdogLogDO row : rows) {
            RestartEventResp event = new RestartEventResp();
            event.setTime(row.getEventTime() == null ? null : row.getEventTime().format(STAMP));
            event.setEventType(row.getEventType());
            event.setReason(row.getDetail());
            event.setRestartCount(row.getRestartCount());
            events.add(event);
            if (row.getEventTime() != null && row.getEventTime().isAfter(hourAgo)
                && !"REBOOT_LIMIT".equals(
                    row.getEventType())) {
                lastHour++;
            }
        }
        resp.setRestarts(events);
        resp.setRestartCountLastHour(lastHour);
        resp.setRestartLimitPerHour(properties.getRestartLimitPerHour());
        resp.setRestartLimitExceeded(lastHour >= properties.getRestartLimitPerHour());
    }

    private void fillSelfCheck(WatchdogStatusResp resp) {
        BizWatchdogLogDO row = watchdogLogMapper.lambdaQuery().eq(BizWatchdogLogDO::getEventType,
            "SELF_CHECK").orderByDesc(BizWatchdogLogDO::getEventTime).last("LIMIT 1").one();
        resp.setSelfCheck(row == null || row.getDetail() == null ? List.of()
            : List.of(new SelfCheckItemResp(
                row.getEventTime() == null ? "最近自检" : row.getEventTime().format(STAMP), true, row
                    .getDetail())));
    }

    private Integer currentRestartCount() {
        Long count = watchdogLogMapper.lambdaQuery().in(BizWatchdogLogDO::getEventType,
            RESTART_EVENT_TYPES).count();
        return count == null ? 0 : count.intValue();
    }

    private List<String> tail(Path path, int lines) {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            List<String> all = Files.readAllLines(path, StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - lines);
            return new ArrayList<>(all.subList(from, all.size()));
        } catch (IOException e) {
            return List.of();
        }
    }

    private Path heartbeatFile() {
        return Paths.get(properties.getDir(), HEARTBEAT_FILE).toAbsolutePath().normalize();
    }

    private Path restartFile() {
        return Paths.get(properties.getDir(), RESTART_FILE).toAbsolutePath().normalize();
    }

    private Path heartbeatLogPath() {
        return Paths.get(properties.getWatchdogLog()).toAbsolutePath().normalize();
    }
}
