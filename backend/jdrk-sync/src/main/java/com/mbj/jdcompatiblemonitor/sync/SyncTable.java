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

package com.mbj.jdcompatiblemonitor.sync;

import java.util.List;
import java.util.Map;

/**
 * 仁科兼容目标库的表定义（FR-SYNC-01/02，列名与 DR-03 脚本一致）。
 *
 * <p>列顺序即写入顺序，时间列用于增量水位与新旧判定，主键列用于幂等 upsert。</p>
 */
public enum SyncTable {

    /** 历史数据表 */
    TBHISTORY("tbhistory", "RecordTime", List.of("ID", "DeviceName", "DeviceAddr", "NodeId", "Hum",
        "Tem", "CoordinateType", "Lng", "Lat", "RecordTime", "IsAlarmData", "Source")),

    /** 报警表（列名 HandingFlag 沿用 DR-03 脚本中的拼写，避免与目标库不一致） */
    TBALARM("tbalarm", "RecordTime",
        List.of("ID", "DeviceName", "DeviceAddr", "NodeId", "AlarmType",
            "AlarmMessage", "DataNo", "DataValue", "AlarmRange", "RecordTime", "HandingFlag",
            "HandlingMethod", "HandlingUser", "HandlingTime", "Lng", "Lat")),

    /** 通知日志表 */
    TBNOTICELOG("tbnoticelog", "RecordTime", List.of("ID", "NoticeType", "DeviceAddr", "NodeId",
        "DeviceName", "Contact", "Content", "RecordTime")),

    /** 系统日志表 */
    TBSYSLOG("tbsyslog", "RecordTime", List.of("ID", "UserName", "Operate", "Detail", "Result",
        "RecordTime"));

    /** 主键列名 */
    public static final String KEY_COLUMN = "ID";

    /**
     * 目标库列宽上限（取自仁科 MySQL 脚本 rkmonitor.sql）。
     *
     * <p>源表字段比目标列宽：sys_log.request_url 是 varchar(512)、error_msg 是 TEXT，
     * 而 tbsyslog.Detail/Result 只有 255。不截断时严格模式的 MySQL 会让**整批**写入失败，
     * 而水位线在水位保存之前抛出、永不推进 —— 同步任务每次都从同一批重来，permanently 卡死。</p>
     */
    private static final Map<String, Map<String, Integer>> LIMITS = Map.of(
        "tbhistory", Map.of("ID", 64, "DeviceName", 255),
        "tbalarm", Map.of("ID", 32, "DeviceName", 255, "AlarmType", 255, "AlarmMessage", 255,
            "AlarmRange", 255, "HandlingMethod", 2000, "HandlingUser", 255),
        "tbnoticelog", Map.of("ID", 32, "NoticeType", 255, "DeviceName", 255, "Contact", 255,
            "Content", 2000),
        "tbsyslog", Map.of("ID", 32, "UserName", 255, "Operate", 255, "Detail", 255, "Result",
            255));

    /**
     * 按目标库列宽截断字符串值（非字符串原样返回）。
     *
     * @param column 列名
     * @param value  原始值
     * @return 截断后的值
     */
    public Object clamp(String column, Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        Integer max = LIMITS.getOrDefault(tableName, Map.of()).get(column);
        return max == null || text.length() <= max ? text : text.substring(0, max);
    }

    private final String tableName;
    private final String timeColumn;
    private final List<String> columns;

    SyncTable(String tableName, String timeColumn, List<String> columns) {
        this.tableName = tableName;
        this.timeColumn = timeColumn;
        this.columns = columns;
    }

    /** 目标库表名。 */
    public String tableName() {
        return tableName;
    }

    /** 记录时间列名。 */
    public String timeColumn() {
        return timeColumn;
    }

    /** 全部列名。 */
    public List<String> columns() {
        return columns;
    }

    /** 除主键外的列名。 */
    public List<String> valueColumns() {
        return columns.stream().filter(name -> !KEY_COLUMN.equals(name)).toList();
    }

    /**
     * 宽松解析表名
     *
     * @param tableName 表名（忽略大小写）
     * @return 表定义，无法识别返回 {@code null}
     */
    public static SyncTable parse(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return null;
        }
        String normalized = tableName.trim().toLowerCase();
        for (SyncTable table : values()) {
            if (table.tableName.equals(normalized)) {
                return table;
            }
        }
        return null;
    }
}
