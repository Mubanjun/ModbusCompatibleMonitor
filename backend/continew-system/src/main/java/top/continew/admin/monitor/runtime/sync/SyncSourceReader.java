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

package top.continew.admin.monitor.runtime.sync;

import com.mbj.jdcompatiblemonitor.sync.SyncRecord;
import com.mbj.jdcompatiblemonitor.sync.SyncTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步来源读取（FR-SYNC-04/07）：从本系统业务库读取待同步数据，并映射为仁科兼容列。
 *
 * <p>主键由「前缀 + 业务主键 + 记录时间」确定性生成，保证重复同步时幂等（FR-SYNC-03）。</p>
 *
 * <p>映射依据 DR-03：历史数据的 `Hum` 取模拟量2、`Tem` 取模拟量1，
 * 与上报引擎写入目标库的口径保持一致。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncSourceReader {

    /** 单条 IN 查询的主键上限 */
    private static final int ID_CHUNK = 500;

    private final DataSource dataSource;

    /**
     * 统计时间范围内的来源条数
     *
     * @param table 目标表
     * @param from  起始时间
     * @param to    结束时间
     * @return 条数
     */
    public long count(SyncTable table, LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT COUNT(*) FROM (" + baseSql(table) + ") t";
        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(from));
            statement.setTimestamp(2, Timestamp.valueOf(to));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception e) {
            throw new IllegalStateException("统计来源数据失败（" + table.tableName() + "）：" + e
                .getMessage(), e);
        }
    }

    /**
     * 按（记录时间, 主键）游标分页读取来源数据
     *
     * @param table      目标表
     * @param from       起始时间（含）
     * @param to         结束时间（含）
     * @param cursorTime 游标时间，首批传 null
     * @param cursorId   游标主键，首批传 null
     * @param limit      单批上限
     * @return 来源记录
     */
    public List<SyncRecord> read(SyncTable table, LocalDateTime from, LocalDateTime to,
        LocalDateTime cursorTime, String cursorId, int limit) {
        boolean firstBatch = cursorTime == null;
        String sql = "SELECT * FROM (" + baseSql(table) + ") t"
            + (firstBatch ? "" : " WHERE t.RecordTime > ? OR (t.RecordTime = ? AND t.sid > ?)")
            + " ORDER BY t.RecordTime, t.sid LIMIT ?";
        List<SyncRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setTimestamp(index++, Timestamp.valueOf(from));
            statement.setTimestamp(index++, Timestamp.valueOf(to));
            if (!firstBatch) {
                statement.setTimestamp(index++, Timestamp.valueOf(cursorTime));
                statement.setTimestamp(index++, Timestamp.valueOf(cursorTime));
                statement.setString(index++, cursorId == null ? "" : cursorId);
            }
            statement.setInt(index, Math.max(limit, 1));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    records.add(this.toRecord(table, rs));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取来源数据失败（" + table.tableName() + "）：" + e
                .getMessage(), e);
        }
        return records;
    }

    private SyncRecord toRecord(SyncTable table, ResultSet rs) throws Exception {
        String id = rs.getString("sid");
        Timestamp timestamp = rs.getTimestamp(table.timeColumn());
        LocalDateTime recordTime = timestamp == null ? null : timestamp.toLocalDateTime();
        Map<String, Object> values = new LinkedHashMap<>();
        for (String column : table.columns()) {
            if (SyncTable.KEY_COLUMN.equals(column)) {
                continue;
            }
            values.put(column, rs.getObject(column));
        }
        return new SyncRecord(id, recordTime, values);
    }

    /**
     * 各目标表对应的来源查询（列名已按 DR-03 对齐）
     *
     * @param table 目标表
     * @return 内层查询语句
     */
    static String baseSql(SyncTable table) {
        return switch (table) {
            case TBHISTORY -> "SELECT CONCAT('H', b.device_id, '-', b.node_id, '-',"
                + " UNIX_TIMESTAMP(b.record_time)) AS sid, b.record_time AS RecordTime,"
                + " MAX(b.device_name) AS DeviceName, MAX(b.device_addr) AS DeviceAddr,"
                + " b.node_id AS NodeId,"
                + " MAX(CASE WHEN b.analog_no = 2 THEN b.data_value END) AS Hum,"
                + " MAX(CASE WHEN b.analog_no = 1 THEN b.data_value END) AS Tem,"
                + " 3 AS CoordinateType, MAX(d.longitude) AS Lng, MAX(d.latitude) AS Lat,"
                + " MAX(b.alarm_flag) AS IsAlarmData, 1 AS Source"
                + " FROM biz_data b LEFT JOIN biz_device d ON d.id = b.device_id"
                + " WHERE b.deleted = 0 AND b.record_time >= ? AND b.record_time <= ?"
                + " GROUP BY b.device_id, b.node_id, b.record_time, b.device_addr";
            case TBALARM -> "SELECT CONCAT('A', r.id) AS sid, r.record_time AS RecordTime,"
                + " r.device_name AS DeviceName, r.device_addr AS DeviceAddr, r.node_id AS NodeId,"
                + " r.alarm_type AS AlarmType, r.alarm_message AS AlarmMessage,"
                + " r.data_no AS DataNo, r.data_value AS DataValue,"
                + " r.alarm_range AS AlarmRange, r.handling_flag AS HandingFlag,"
                + " r.handling_method AS HandlingMethod, r.handling_user AS HandlingUser,"
                + " r.handling_time AS HandlingTime, r.lng AS Lng, r.lat AS Lat"
                + " FROM biz_alarm_record r"
                + " WHERE r.deleted = 0 AND r.record_time >= ? AND r.record_time <= ?";
            case TBNOTICELOG -> "SELECT CONCAT('N', n.id) AS sid,"
                + " COALESCE(n.send_time, n.create_time) AS RecordTime,"
                + " n.notify_type AS NoticeType, r.device_addr AS DeviceAddr, r.node_id AS NodeId,"
                + " r.device_name AS DeviceName, n.target AS Contact, n.content AS Content"
                + " FROM biz_alarm_notify n LEFT JOIN biz_alarm_record r ON r.id = n.record_id"
                + " WHERE n.deleted = 0 AND COALESCE(n.send_time, n.create_time) >= ?"
                + " AND COALESCE(n.send_time, n.create_time) <= ?";
            case TBSYSLOG -> "SELECT CONCAT('S', l.id) AS sid, l.create_time AS RecordTime,"
                + " CAST(COALESCE(l.create_user, 0) AS CHAR) AS UserName,"
                + " l.description AS Operate,"
                + " CONCAT(COALESCE(l.request_method, ''), ' ', COALESCE(l.request_url, '')) AS Detail,"
                + " CASE WHEN l.status = 1 THEN '成功' ELSE COALESCE(l.error_msg, '失败') END AS Result"
                + " FROM sys_log l WHERE l.create_time >= ? AND l.create_time <= ?";
        };
    }
}
