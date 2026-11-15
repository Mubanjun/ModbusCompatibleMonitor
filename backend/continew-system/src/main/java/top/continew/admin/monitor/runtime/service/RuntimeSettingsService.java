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

package top.continew.admin.monitor.runtime.service;

import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizSysConfigMapper;
import top.continew.admin.monitor.model.entity.BizSysConfigDO;
import com.mbj.jdcompatiblemonitor.device.runtime.AcquisitionSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集参数解析：以 {@code biz_sys_config}（COLLECT 分类）为准，缺项回落到内置默认值。
 *
 * <p>结果带 10 秒缓存，使 FR-CFG-01「配置生效且持久化」在不用重启的前提下生效，
 * 同时避免每 200ms 的调度轮询都去打数据库。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeSettingsService {

    /** 全局采集间隔 */
    public static final String KEY_INTERVAL = "COLLECT_INTERVAL_MS";
    /** 单帧超时 */
    public static final String KEY_TIMEOUT = "COLLECT_TIMEOUT_MS";
    /** 失败重试次数 */
    public static final String KEY_RETRY = "COLLECT_RETRY_COUNT";
    /** 熔断阈值 */
    public static final String KEY_FUSE_THRESHOLD = "COLLECT_FUSE_THRESHOLD";
    /** 单设备轮询预算 */
    public static final String KEY_POLL_BUDGET = "COLLECT_POLL_BUDGET_MS";
    /** 熔断打开时长 */
    public static final String KEY_CIRCUIT_OPEN = "COLLECT_CIRCUIT_OPEN_MS";
    /** 退避上限 */
    public static final String KEY_MAX_BACKOFF = "COLLECT_MAX_BACKOFF_MS";
    /** 夜间间隔倍数 */
    public static final String KEY_NIGHT_MULTIPLIER = "COLLECT_NIGHT_MULTIPLIER";
    /** 夜间起始小时 */
    public static final String KEY_NIGHT_START = "COLLECT_NIGHT_START_HOUR";
    /** 夜间结束小时 */
    public static final String KEY_NIGHT_END = "COLLECT_NIGHT_END_HOUR";

    /** 存储配置：暂存容量上限 */
    public static final String KEY_BUFFER_MAX_ROWS = "STORAGE_BUFFER_MAX_ROWS";
    /** 存储配置：历史数据保留天数 */
    public static final String KEY_RETAIN_DAYS = "STORAGE_RETAIN_DAYS";

    /**
     * 磁盘剩余空间告警阈值（MB，NFR-FAULT-03）
     */
    public static final String KEY_MIN_FREE_MB = "STORAGE_MIN_FREE_MB";

    /**
     * 磁盘剩余空间保护阈值（MB，NFR-FAULT-03）
     */
    public static final String KEY_PROTECT_FREE_MB = "STORAGE_PROTECT_FREE_MB";
    /** 报警配置：短信通知目标 */
    public static final String KEY_ALARM_SMS_TARGET = "ALARM_NOTIFY_SMS_TARGET";
    /** 报警配置：邮件通知目标 */
    public static final String KEY_ALARM_EMAIL_TARGET = "ALARM_NOTIFY_EMAIL_TARGET";
    /** 报警配置：MQTT 通知主题 */
    public static final String KEY_ALARM_MQTT_TOPIC = "ALARM_NOTIFY_MQTT_TOPIC";
    /** 报警配置：MQTT Broker 地址 */
    public static final String KEY_ALARM_MQTT_HOST = "ALARM_NOTIFY_MQTT_HOST";
    /** 报警配置：MQTT Broker 端口 */
    public static final String KEY_ALARM_MQTT_PORT = "ALARM_NOTIFY_MQTT_PORT";
    /** 报警配置：MQTT 用户名 */
    public static final String KEY_ALARM_MQTT_USERNAME = "ALARM_NOTIFY_MQTT_USERNAME";
    /** 报警配置：MQTT 口令 */
    public static final String KEY_ALARM_MQTT_PASSWORD = "ALARM_NOTIFY_MQTT_PASSWORD";

    private static final String CATEGORY_COLLECT = "COLLECT";
    private static final String CATEGORY_STORAGE = "STORAGE";
    private static final String CATEGORY_ALARM = "ALARM";
    private static final String CATEGORY_SYSTEM = "SYSTEM";
    private static final long CACHE_MILLIS = 10_000L;

    private final BizSysConfigMapper bizSysConfigMapper;

    private volatile AcquisitionSettings cached;
    private volatile long cachedAt;
    private volatile Map<String, String> storageValues = Map.of();
    private volatile Map<String, String> alarmValues = Map.of();
    private volatile Map<String, String> systemValues = Map.of();

    /**
     * 当前生效的采集参数
     *
     * @return 采集参数
     */
    public AcquisitionSettings current() {
        AcquisitionSettings local = cached;
        if (local != null && System.currentTimeMillis() - cachedAt < CACHE_MILLIS) {
            return local;
        }
        return this.reload();
    }

    /**
     * 强制重新读取配置
     *
     * @return 采集参数
     */
    public synchronized AcquisitionSettings reload() {
        AcquisitionSettings defaults = AcquisitionSettings.defaults();
        Map<String, String> values = this.loadValues(CATEGORY_COLLECT);
        storageValues = this.loadValues(CATEGORY_STORAGE);
        alarmValues = this.loadValues(CATEGORY_ALARM);
        systemValues = this.loadValues(CATEGORY_SYSTEM);
        AcquisitionSettings settings = new AcquisitionSettings(
            this.intValue(values, KEY_INTERVAL, defaults.intervalMs()),
            this.intValue(values, KEY_TIMEOUT, defaults.timeoutMs()),
            this.intValue(values, KEY_RETRY, defaults.retries()),
            this.intValue(values, KEY_POLL_BUDGET, defaults.pollBudgetMs()),
            this.intValue(values, KEY_FUSE_THRESHOLD, defaults.failureThreshold()),
            this.intValue(values, KEY_CIRCUIT_OPEN, defaults.circuitOpenMs()),
            this.intValue(values, KEY_MAX_BACKOFF, defaults.maxBackoffMs()),
            this.intValue(values, KEY_NIGHT_MULTIPLIER, defaults.nightMultiplier()),
            this.intValue(values, KEY_NIGHT_START, defaults.nightStartHour()),
            this.intValue(values, KEY_NIGHT_END, defaults.nightEndHour()));
        cached = settings;
        cachedAt = System.currentTimeMillis();
        return settings;
    }

    /**
     * 读取 COLLECT 分类下的全部配置项
     *
     * @return 参数编码到参数值的映射
     */
    private Map<String, String> loadValues(String category) {
        Map<String, String> values = new HashMap<>();
        try {
            List<BizSysConfigDO> rows = bizSysConfigMapper.lambdaQuery()
                .eq(BizSysConfigDO::getCategory, category)
                .list();
            for (BizSysConfigDO row : rows) {
                if (row.getCode() == null || row.getValue() == null) {
                    continue;
                }
                values.put(row.getCode(), row.getValue());
            }
        } catch (Exception e) {
            // 启动早期（建表前）或数据库不可用时回落到内置默认值，不能因此阻断采集
            log.warn("读取业务系统配置失败（{}），参数回落到内置默认值：{}", category, e.getMessage());
        }
        return values;
    }

    /**
     * 解析整型配置项
     *
     * @param values       配置映射
     * @param key          参数编码
     * @param defaultValue 默认值
     * @return 生效值
     */
    private int intValue(Map<String, String> values, String key, int defaultValue) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        Integer parsed = Convert.toInt(raw.trim(), null);
        return parsed == null ? defaultValue : parsed;
    }

    /**
     * 取 STORAGE 分类下的长整型配置（FR-CFG-03）
     *
     * @param key          参数编码
     * @param defaultValue 默认值
     * @return 生效值，最小为 1
     */
    public long storageLong(String key, long defaultValue) {
        this.current();
        String raw = storageValues.get(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        Long parsed = Convert.toLong(raw.trim(), null);
        return parsed == null || parsed <= 0L ? defaultValue : parsed;
    }

    /**
     * 暂存容量上限（行数）
     *
     * @param defaultValue 默认值
     * @return 生效值
     */
    public long bufferMaxRows(long defaultValue) {
        return this.storageLong(KEY_BUFFER_MAX_ROWS, defaultValue);
    }

    /**
     * 磁盘剩余空间告警阈值（MB，NFR-FAULT-03）
     *
     * @param defaultValue 默认值
     * @return 生效值
     */
    public long minFreeMb(long defaultValue) {
        return this.storageLong(KEY_MIN_FREE_MB, defaultValue);
    }

    /**
     * 磁盘剩余空间保护阈值（MB，NFR-FAULT-03）
     *
     * @param defaultValue 默认值
     * @return 生效值
     */
    public long protectFreeMb(long defaultValue) {
        return this.storageLong(KEY_PROTECT_FREE_MB, defaultValue);
    }

    /**
     * 历史数据保留天数
     *
     * @param defaultValue 默认值
     * @return 生效值
     */
    public int historyRetainDays(int defaultValue) {
        return (int) Math.min(this.storageLong(KEY_RETAIN_DAYS, defaultValue), 3650L);
    }

    /**
     * 取 SYSTEM 分类下的整型配置（FR-CFG-04、FR-PROTO-09）
     *
     * @param key          参数编码
     * @param defaultValue 默认值
     * @return 生效值
     */
    public int systemInt(String key, int defaultValue) {
        this.current();
        String raw = systemValues.get(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        Integer parsed = Convert.toInt(raw.trim(), null);
        return parsed == null ? defaultValue : parsed;
    }

    /**
     * 取 ALARM 分类下的配置（FR-ALM-04 通知目标）
     *
     * @param key          参数编码
     * @param defaultValue 默认值
     * @return 生效值
     */
    public String alarmTarget(String key, String defaultValue) {
        this.current();
        String raw = alarmValues.get(key);
        return raw == null || raw.isBlank() ? defaultValue : raw.trim();
    }
}
