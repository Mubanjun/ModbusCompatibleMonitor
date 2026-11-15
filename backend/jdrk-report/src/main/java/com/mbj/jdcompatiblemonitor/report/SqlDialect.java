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

import java.util.List;
import java.util.StringJoiner;

/**
 * 上报目标库方言（FR-RPT-03：支持 MySQL、MariaDB、PostgreSQL、SQL Server、KingbaseES、OracleDM 等）。
 *
 * <p>方言差异集中在三处：驱动类名、标识符引用符、是否需要为标识符大小写加引号。
 * 占位符统一使用 {@code ?}，因此插入语句可共用同一套模板。</p>
 *
 * <p>除 MySQL/MariaDB 外，其余驱动需由实施方按目标库自行提供（放入运行目录的 lib 即可），
 * 未提供时会在上报失败原因中明确提示。</p>
 */
public enum SqlDialect {

    /** MySQL */
    MYSQL("com.mysql.cj.jdbc.Driver", '`', '`'),
    /** MariaDB */
    MARIADB("org.mariadb.jdbc.Driver", '`', '`'),
    /** PostgreSQL */
    POSTGRESQL("org.postgresql.Driver", '"', '"'),
    /** SQL Server */
    SQLSERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver", '[', ']'),
    /** 人大金仓 KingbaseES */
    KINGBASE("com.kingbase8.Driver", '"', '"'),
    /** Oracle */
    ORACLE("oracle.jdbc.OracleDriver", '"', '"'),
    /** 达梦 DM */
    DM("dm.jdbc.driver.DmDriver", '"', '"');

    private final String driverClass;
    private final char quoteStart;
    private final char quoteEnd;

    SqlDialect(String driverClass, char quoteStart, char quoteEnd) {
        this.driverClass = driverClass;
        this.quoteStart = quoteStart;
        this.quoteEnd = quoteEnd;
    }

    /** JDBC 驱动类名。 */
    public String driverClass() {
        return driverClass;
    }

    /**
     * 引用标识符
     *
     * @param identifier 标识符
     * @return 引用后的标识符
     */
    public String quote(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return quoteStart + "" + quoteEnd;
        }
        // 标识符内部的引用符必须按 SQL 标准双写转义（反引号/双引号/方括号各自成对），
        // 否则配置里带引号的表名会拼出非法 SQL。
        String escaped = identifier.indexOf(quoteEnd) < 0 ? identifier : identifier.replace(
            String.valueOf(quoteEnd), String.valueOf(quoteEnd) + quoteEnd);
        return quoteStart + escaped + quoteEnd;
    }

    /**
     * 生成插入语句
     *
     * @param table   表名
     * @param columns 列名
     * @return INSERT 语句
     */
    public String insertSql(String table, List<String> columns) {
        StringJoiner names = new StringJoiner(", ");
        StringJoiner values = new StringJoiner(", ");
        for (String column : columns) {
            names.add(this.quote(column));
            values.add("?");
        }
        return "INSERT INTO " + this.quote(table) + " (" + names + ") VALUES (" + values + ")";
    }

    /**
     * 宽松解析方言
     *
     * @param value 方言名称
     * @return 方言，无法识别返回 {@link #MYSQL}
     */
    public static SqlDialect parse(String value) {
        if (value == null || value.isBlank()) {
            return MYSQL;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MYSQL;
        }
    }
}
