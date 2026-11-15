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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mbj.jdcompatiblemonitor.report.SqlDialect;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 目标库表定义与写入语句的单元测试（FR-SYNC-01/03）。 */
class UpsertStatementTest {

    @Test
    void declaresRuoKeColumns() {
        assertEquals(12, SyncTable.TBHISTORY.columns().size());
        assertTrue(SyncTable.TBHISTORY.columns().contains("Hum"));
        assertTrue(SyncTable.TBHISTORY.columns().contains("Tem"));
        assertTrue(SyncTable.TBHISTORY.columns().contains("IsAlarmData"));
        assertEquals(16, SyncTable.TBALARM.columns().size());
        assertTrue(SyncTable.TBALARM.columns().contains("HandingFlag"));
        assertEquals("RecordTime", SyncTable.TBHISTORY.timeColumn());
        assertEquals(11, SyncTable.TBHISTORY.valueColumns().size());
        assertFalse(SyncTable.TBHISTORY.valueColumns().contains("ID"));
    }

    @Test
    void parsesTableNames() {
        assertEquals(SyncTable.TBHISTORY, SyncTable.parse("tbhistory"));
        assertEquals(SyncTable.TBALARM, SyncTable.parse(" TBALARM "));
        assertEquals(SyncTable.TBSYSLOG, SyncTable.parse("tbsyslog"));
        assertNull(SyncTable.parse("unknown"));
        assertNull(SyncTable.parse(null));
    }

    @Test
    void generatesMysqlNativeUpsert() {
        String sql = UpsertStatement.nativeUpsert(SqlDialect.MYSQL, SyncTable.TBHISTORY);
        String quoted = "INSERT INTO " + SqlDialect.MYSQL.quote(SyncTable.TBHISTORY.tableName());
        assertTrue(sql.startsWith(quoted), sql);
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"), sql);
        String hum = SqlDialect.MYSQL.quote("Hum");
        assertTrue(sql.contains(hum + " = VALUES(" + hum + ")"), sql);
        assertFalse(sql.contains(SqlDialect.MYSQL.quote("ID") + " = VALUES("), "主键不参与更新");
    }

    @Test
    void generatesPostgresNativeUpsert() {
        String sql = UpsertStatement.nativeUpsert(SqlDialect.POSTGRESQL, SyncTable.TBSYSLOG);
        String quote = "\"";
        assertTrue(sql.contains("ON CONFLICT (" + quote + "ID" + quote + ") DO UPDATE SET"), sql);
        String user = "\"UserName\"";
        assertTrue(sql.contains(user + " = EXCLUDED." + user), sql);
    }

    @Test
    void reportsNativeUpsertSupport() {
        assertTrue(UpsertStatement.supportsNativeUpsert(SqlDialect.MYSQL));
        assertTrue(UpsertStatement.supportsNativeUpsert(SqlDialect.POSTGRESQL));
        assertTrue(UpsertStatement.supportsNativeUpsert(SqlDialect.KINGBASE));
        assertFalse(UpsertStatement.supportsNativeUpsert(SqlDialect.SQLSERVER));
        assertFalse(UpsertStatement.supportsNativeUpsert(SqlDialect.ORACLE));
        assertFalse(UpsertStatement.supportsNativeUpsert(SqlDialect.DM));
    }

    @Test
    void generatesFallbackStatements() {
        String update = UpsertStatement.updateById(SqlDialect.SQLSERVER, SyncTable.TBNOTICELOG);
        assertTrue(update.startsWith("UPDATE [tbnoticelog] SET "), update);
        assertTrue(update.endsWith("WHERE [ID] = ?"), update);
        String select = UpsertStatement.selectByIds(SqlDialect.SQLSERVER, SyncTable.TBNOTICELOG, 3);
        assertTrue(select.contains("IN (?, ?, ?)"), select);
        assertEquals("SELECT COUNT(*) FROM " + SqlDialect.MYSQL.quote("tbalarm"),
            UpsertStatement.count(SqlDialect.MYSQL, SyncTable.TBALARM));
    }

    @Test
    void generatesIncrementalSelect() {
        String sql = UpsertStatement.selectNewerThan(SqlDialect.MYSQL, SyncTable.TBHISTORY);
        String time = SqlDialect.MYSQL.quote("RecordTime");
        assertTrue(sql.contains(time + " >= ?"), sql);
        assertTrue(sql.contains("ORDER BY " + time + ", " + SqlDialect.MYSQL.quote("ID")), sql);
    }

    @Test
    void ordersValuesByColumnSequence() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("Source", 1);
        values.put("DeviceName", "1 号站");
        SyncRecord record = new SyncRecord("id-1", LocalDateTime.of(2026, 9, 23, 10, 0), values);
        Object[] ordered = record.orderedValues(SyncTable.TBHISTORY);
        assertEquals("1 号站", ordered[0], "第 1 列应为 DeviceName");
        assertEquals(1, ordered[10], "最后 1 列应为 Source");
    }
}
