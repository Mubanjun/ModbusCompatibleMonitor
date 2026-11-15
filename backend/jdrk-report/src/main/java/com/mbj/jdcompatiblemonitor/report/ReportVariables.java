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

package com.mbj.jdcompatiblemonitor.report;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上报模板变量（FR-RPT-01 主题模板、FR-RPT-02 自定义模板）。
 *
 * <p>变量名与字段名一致，便于「字段映射」规则同时作用于模板与 JSON 报文。</p>
 */
public final class ReportVariables {

    /**
     * 对外报文体格式版本（NFR-MAINT-07）
     *
     * <p>第三方集成方可用它判断字段契约：同一大版本内只做**向后兼容的增量**（新增字段/变量），
     * 删改字段必须升版本。备份包另有独立版本标识（JDRKBK1）。</p>
     */
    public static final int SCHEMA_VERSION = 1;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ReportVariables() {
    }

    /**
     * 单条数据的变量
     *
     * @param item 采样数据
     * @return 变量表
     */
    public static Map<String, Object> ofItem(ReportItem item) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("deviceAddr", item.deviceAddr());
        variables.put("deviceName", item.deviceName());
        variables.put("nodeId", item.nodeId());
        variables.put("analogNo", item.analogNo());
        variables.put("elementType", item.elementType());
        variables.put("elementName", item.elementName());
        variables.put("unit", item.unit());
        variables.put("value", item.value());
        variables.put("rawValue", item.rawValue());
        variables.put("collectTime", formatTime(item.collectTime()));
        variables.put("collectTimestamp", item.collectTime());
        variables.put("alarm", item.alarm() ? 1 : 0);
        variables.put("lng", item.lng());
        variables.put("lat", item.lat());
        return variables;
    }

    /**
     * 批次的变量
     *
     * @param batch 批次
     * @return 变量表
     */
    public static Map<String, Object> ofBatch(ReportBatch batch) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("ruleId", batch.ruleId());
        variables.put("ruleName", batch.ruleName());
        variables.put("targetId", batch.targetId());
        variables.put("targetName", batch.targetName());
        variables.put("portName", batch.portName());
        variables.put("batchTime", formatTime(batch.batchTime()));
        variables.put("batchTimestamp", batch.batchTime());
        variables.put("count", batch.count());
        // 格式版本随报文一起下发：集成方据此断言字段契约（NFR-MAINT-07）
        variables.put("schemaVersion", SCHEMA_VERSION);
        return variables;
    }

    /**
     * 格式化时间戳
     *
     * @param millis 毫秒时间戳
     * @return {@code yyyy-MM-dd HH:mm:ss}
     */
    public static String formatTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(TIME_FORMAT);
    }
}
