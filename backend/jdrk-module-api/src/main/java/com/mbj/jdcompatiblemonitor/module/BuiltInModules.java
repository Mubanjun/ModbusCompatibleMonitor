/*
 * Copyright (c) 2026-present 仁科兼容环境监测平台 Authors. All Rights Reserved.
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

package com.mbj.jdcompatiblemonitor.module;

import java.util.List;

/** V1.4 内置业务模块 manifest 清单。 */
public final class BuiltInModules {

    public static final List<ModuleManifest> ALL = List.of(
        new ModuleManifest("device", "设备管理", "1.0.0", true, List.of(), "/env", "device",
            List.of("biz_device", "biz_device_group", "biz_sensor", "biz_sensor_reading"),
            "db/changelog/module-device.sql", "modules/device", List.of("device-summary", "map")),
        new ModuleManifest("acquisition", "采集与监视", "1.0.0", true, List.of("device"), "/env",
            "acq", List.of("biz_port", "biz_data", "biz_signal_log"),
            "db/changelog/module-acquisition.sql",
            "modules/acquisition", List.of("acquisition-summary")),
        new ModuleManifest("alarm", "报警", "1.0.0", true, List.of("device"), "/env", "alarm",
            List.of("biz_alarm_rule", "biz_alarm_record", "biz_alarm_notify"),
            "db/changelog/module-alarm.sql",
            "modules/alarm", List.of("alarm-recent")),
        new ModuleManifest("report", "数据上报", "1.0.0", true, List.of("device"), "/env", "report",
            List.of("biz_report_target", "biz_report_rule", "biz_report_dead_letter"),
            "db/changelog/module-report.sql", "modules/report", List.of("report-quality")),
        new ModuleManifest("sync", "数据同步", "1.0.0", true, List.of("device"), "/env", "sync",
            List.of("biz_sync_task", "biz_sync_conflict", "biz_sync_watermark"),
            "db/changelog/module-sync.sql",
            "modules/sync", List.of("sync-watermark")),
        new ModuleManifest("analytics", "查询与分析", "1.0.0", true, List.of("device"), "/env", "ana",
            List.of(), "db/changelog/module-analytics.sql", "modules/analytics",
            List.of("analysis-board")),
        new ModuleManifest("ops", "运维与诊断", "1.0.0", true, List.of(), "/env", "ops",
            List.of("biz_watchdog_log"), "db/changelog/module-ops.sql", "modules/ops",
            List.of("self-check")),
        new ModuleManifest("predict", "预测（预留）", "1.0.0", true, List.of(), null, "pred",
            List.of(), "db/changelog/module-predict.sql", "modules/predict", List.of()));

    private BuiltInModules() {
    }
}
