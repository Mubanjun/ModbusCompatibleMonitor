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

package top.continew.admin.monitor.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 壳仪表盘聚合接口：一次返回整页数据，禁止前端并发打十多个接口。 */
@Tag(name = "仪表盘聚合 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/dashboard")
public class MonitorDashboardController {

    private static final long CACHE_MS = 15_000L;
    private final JdbcTemplate jdbc;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return cached("overview", this::buildOverview);
    }

    @GetMapping("/analysis")
    public Map<String, Object> analysis(@RequestParam(defaultValue = "7") int days) {
        int window = days == 30 ? 30 : 7;
        return cached("analysis-" + window, () -> buildAnalysis(window));
    }

    private Map<String, Object> buildOverview() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("runtimes", runtimes());
        root.put("selfCheck", selfCheck());
        Map<String, Object> acquisition = new LinkedHashMap<>();
        acquisition.put("devices", queryLong("SELECT COUNT(*) FROM biz_device WHERE deleted = 0"));
        acquisition.put("sensors", queryLong("SELECT COUNT(*) FROM biz_sensor WHERE deleted = 0"));
        acquisition.put("todayReadings",
            queryLong("SELECT COUNT(*) FROM biz_sensor_reading WHERE record_time >= CURDATE()"));
        acquisition.put("onlineDevices", queryLong(
            "SELECT COUNT(DISTINCT device_id) FROM biz_data_latest WHERE create_time >= DATE_SUB(NOW(), INTERVAL 5 MINUTE)"));
        root.put("acquisition", acquisition);
        root.put("groups",
            queryList("SELECT g.name AS name, COUNT(d.id) AS value FROM biz_device_group g "
                + "LEFT JOIN biz_device d ON d.group_id = g.id AND d.deleted = 0 WHERE g.deleted = 0 "
                + "GROUP BY g.id, g.name ORDER BY value DESC LIMIT 10"));
        root.put("recentAlarms",
            queryList("SELECT device_name AS deviceName, alarm_type AS alarmType, "
                + "alarm_message AS alarmMessage, record_time AS recordTime FROM biz_alarm_record "
                + "WHERE deleted = 0 ORDER BY record_time DESC LIMIT 10"));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("deadLetters",
            queryLong("SELECT COUNT(*) FROM biz_report_dead_letter WHERE state = 'DEAD'"));
        report.put("sentBatches",
            queryLong("SELECT COUNT(*) FROM biz_report_dead_letter WHERE state = 'REPLAYED'"));
        root.put("report", report);
        Map<String, Object> todos = new LinkedHashMap<>();
        todos.put("unhandledAlarms", queryLong(
            "SELECT COUNT(*) FROM biz_alarm_record WHERE deleted = 0 AND handling_flag = 0"));
        todos.put("deadLetters", report.get("deadLetters"));
        todos.put("diskFreeMb", queryLong("SELECT 0"));
        root.put("todos", todos);
        return root;
    }

    private Map<String, Object> runtimes() {
        long processUptime = ManagementFactory.getRuntimeMXBean().getUptime();
        long managerUptime = ProcessHandle.current().parent()
            .flatMap(ph -> ph.info().startInstant())
            .map(start -> Duration.between(start, Instant.now()).toMillis())
            .orElse(processUptime);
        Long serviceSeconds =
            queryLong("SELECT TIMESTAMPDIFF(SECOND, MIN(event_time), NOW()) FROM biz_watchdog_log "
                + "WHERE event_type IN ('RESTART','SELF_CHECK') AND event_type IS NOT NULL");
        long serviceUptime =
            serviceSeconds == null || serviceSeconds < 0 ? processUptime : serviceSeconds * 1000L;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processUptimeMs", processUptime);
        result.put("managerUptimeMs", managerUptime);
        result.put("serviceUptimeMs", serviceUptime);
        result.put("labels", List.of("后端进程", "管理器连续", "服务可用"));
        return result;
    }

    private Map<String, Object> selfCheck() {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(checkSql("数据库", "SELECT 1"));
        checks.add(checkSql("分组", "SELECT COUNT(*) FROM biz_device_group WHERE deleted = 0"));
        checks.add(checkSql("设备", "SELECT COUNT(*) FROM biz_device WHERE deleted = 0"));
        checks.add(checkSql("传感器", "SELECT COUNT(*) FROM biz_sensor WHERE deleted = 0"));
        checks.add(checkSql("读数", "SELECT COUNT(*) FROM biz_sensor_reading"));
        long passed = checks.stream().filter(c -> Boolean.TRUE.equals(c.get("ok"))).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", passed);
        result.put("total", checks.size());
        result.put("items", checks);
        return result;
    }

    private Map<String, Object> checkSql(String name, String sql) {
        try {
            jdbc.queryForObject(sql, Long.class);
            return check(name, true, "可读");
        } catch (Exception e) {
            return check(name, false, e.getMessage());
        }
    }

    private Map<String, Object> check(String name, boolean ok, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("ok", ok);
        item.put("detail", detail);
        return item;
    }

    private Map<String, Object> buildAnalysis(int days) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("days", days);
        root.put("sampleTrend", queryList("SELECT DATE(record_time) AS day, COUNT(*) AS value "
            + "FROM biz_sensor_reading WHERE record_time >= DATE_SUB(CURDATE(), INTERVAL " + days
            + " DAY) GROUP BY DATE(record_time) ORDER BY day"));
        root.put("alarmTrend", queryList("SELECT DATE(record_time) AS day, COUNT(*) AS value "
            + "FROM biz_alarm_record WHERE deleted = 0 AND record_time >= DATE_SUB(CURDATE(), INTERVAL "
            + days
            + " DAY) GROUP BY DATE(record_time) ORDER BY day"));
        root.put("elementTop",
            queryList("SELECT element_code AS name, COUNT(*) AS value FROM biz_sensor "
                + "WHERE deleted = 0 GROUP BY element_code ORDER BY value DESC LIMIT 10"));
        root.put("portHealth",
            queryList("SELECT p.name AS name, COUNT(d.id) AS devices FROM biz_port p "
                + "LEFT JOIN biz_device d ON d.port_id = p.id AND d.deleted = 0 WHERE p.deleted = 0 "
                + "GROUP BY p.id, p.name ORDER BY devices DESC LIMIT 10"));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("deadLetters",
            queryLong("SELECT COUNT(*) FROM biz_report_dead_letter WHERE state = 'DEAD'"));
        report.put("replayed",
            queryLong("SELECT COUNT(*) FROM biz_report_dead_letter WHERE state = 'REPLAYED'"));
        report.put("replayFailed",
            queryLong("SELECT COUNT(*) FROM biz_report_dead_letter WHERE state = 'REPLAY_FAILED'"));
        root.put("reportQuality", report);
        root.put("abnormalTop",
            queryList("SELECT device_name AS name, COUNT(*) AS value FROM biz_alarm_record "
                + "WHERE deleted = 0 GROUP BY device_name ORDER BY value DESC LIMIT 10"));
        return root;
    }

    private Map<String, Object> cached(String key,
        java.util.function.Supplier<Map<String, Object>> builder) {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(key);
        if (entry != null && now - entry.at() < CACHE_MS) {
            return entry.value();
        }
        Map<String, Object> value = builder.get();
        cache.put(key, new CacheEntry(now, value));
        return value;
    }

    private long queryLong(String sql) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private List<Map<String, Object>> queryList(String sql) {
        try {
            return jdbc.queryForList(sql);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private record CacheEntry(long at, Map<String, Object> value) {
    }
}
