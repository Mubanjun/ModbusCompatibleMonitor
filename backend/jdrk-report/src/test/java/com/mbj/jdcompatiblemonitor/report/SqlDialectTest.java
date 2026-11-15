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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 上报目标库方言的单元测试（FR-RPT-03）。 */
class SqlDialectTest {

    @Test
    void generatesInsertWithDialectQuoting() {
        List<String> columns = List.of("ID", "DeviceName", "RecordTime");
        assertEquals("INSERT INTO `tbhistory` (`ID`, `DeviceName`, `RecordTime`) VALUES (?, ?, ?)",
            SqlDialect.MYSQL.insertSql("tbhistory", columns));
        assertEquals(
            "INSERT INTO \"tbhistory\" (\"ID\", \"DeviceName\", \"RecordTime\") VALUES (?, ?, ?)",
            SqlDialect.POSTGRESQL.insertSql("tbhistory", columns));
        assertEquals("INSERT INTO [tbhistory] ([ID], [DeviceName], [RecordTime]) VALUES (?, ?, ?)",
            SqlDialect.SQLSERVER.insertSql("tbhistory", columns));
    }

    @Test
    void exposesDriverClasses() {
        assertEquals("com.mysql.cj.jdbc.Driver", SqlDialect.MYSQL.driverClass());
        assertEquals("org.postgresql.Driver", SqlDialect.POSTGRESQL.driverClass());
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver",
            SqlDialect.SQLSERVER.driverClass());
        assertEquals("com.kingbase8.Driver", SqlDialect.KINGBASE.driverClass());
        assertEquals("oracle.jdbc.OracleDriver", SqlDialect.ORACLE.driverClass());
        assertEquals("dm.jdbc.driver.DmDriver", SqlDialect.DM.driverClass());
    }

    @Test
    void parsesDialectNames() {
        assertEquals(SqlDialect.POSTGRESQL, SqlDialect.parse("postgresql"));
        assertEquals(SqlDialect.MARIADB, SqlDialect.parse(" MariaDB "));
        assertEquals(SqlDialect.MYSQL, SqlDialect.parse("unknown"));
        assertEquals(SqlDialect.MYSQL, SqlDialect.parse(null));
    }

    @Test
    void escapesQuoteCharactersInsideIdentifiers() {
        // 标识符里的引用符必须双写，否则拼出来的 SQL 语法就断了
        assertEquals("`a``b`", SqlDialect.MYSQL.quote("a`b"));
        assertEquals("\"a\"\"b\"", SqlDialect.POSTGRESQL.quote("a\"b"));
        assertEquals("[a]]b]", SqlDialect.SQLSERVER.quote("a]b"));
        assertEquals("`tbhistory`", SqlDialect.MYSQL.quote("tbhistory"));
    }
}
