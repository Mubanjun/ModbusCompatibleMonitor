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

import com.mbj.jdcompatiblemonitor.report.SqlDialect;

import java.util.StringJoiner;

/**
 * 目标库写入语句生成（FR-SYNC-03 幂等 upsert、FR-RPT-03 多方言）。
 *
 * <p>MySQL/MariaDB 使用 {@code ON DUPLICATE KEY UPDATE}，PostgreSQL/KingbaseES 使用
 * {@code ON CONFLICT ... DO UPDATE}；SQL Server/Oracle/达梦 无统一语法，
 * 由上层按「先按主键查询、存在则更新、不存在则插入」的两步流程处理。</p>
 */
public final class UpsertStatement {

    private UpsertStatement() {
    }

    /**
     * 该方言是否支持单语句原生 upsert
     *
     * @param dialect 方言
     * @return 支持返回 true
     */
    public static boolean supportsNativeUpsert(SqlDialect dialect) {
        return switch (dialect) {
            case MYSQL, MARIADB, POSTGRESQL, KINGBASE -> true;
            default -> false;
        };
    }

    /**
     * 生成原生 upsert 语句
     *
     * @param dialect 方言
     * @param table   目标表
     * @return 语句
     */
    public static String nativeUpsert(SqlDialect dialect, SyncTable table) {
        String base = insert(dialect, table);
        if (dialect == SqlDialect.POSTGRESQL || dialect == SqlDialect.KINGBASE) {
            StringJoiner assignments = new StringJoiner(", ");
            for (String column : table.valueColumns()) {
                assignments.add(dialect.quote(column) + " = EXCLUDED." + dialect.quote(column));
            }
            return base + " ON CONFLICT (" + dialect.quote(SyncTable.KEY_COLUMN)
                + ") DO UPDATE SET " + assignments;
        }
        StringJoiner assignments = new StringJoiner(", ");
        for (String column : table.valueColumns()) {
            assignments.add(dialect.quote(column) + " = VALUES(" + dialect.quote(column) + ")");
        }
        return base + " ON DUPLICATE KEY UPDATE " + assignments;
    }

    /**
     * 生成插入语句
     *
     * @param dialect 方言
     * @param table   目标表
     * @return 语句
     */
    public static String insert(SqlDialect dialect, SyncTable table) {
        return dialect.insertSql(table.tableName(), table.columns());
    }

    /**
     * 生成按主键更新的语句
     *
     * @param dialect 方言
     * @param table   目标表
     * @return 语句
     */
    public static String updateById(SqlDialect dialect, SyncTable table) {
        StringJoiner assignments = new StringJoiner(", ");
        for (String column : table.valueColumns()) {
            assignments.add(dialect.quote(column) + " = ?");
        }
        return "UPDATE " + dialect.quote(table.tableName()) + " SET " + assignments + " WHERE "
            + dialect.quote(SyncTable.KEY_COLUMN) + " = ?";
    }

    /**
     * 生成按主键批量查询的语句（用于存在性判断与冲突比较）
     *
     * @param dialect 方言
     * @param table   目标表
     * @param count   主键个数
     * @return 语句
     */
    public static String selectByIds(SqlDialect dialect, SyncTable table, int count) {
        StringJoiner placeholders = new StringJoiner(", ");
        for (int i = 0; i < count; i++) {
            placeholders.add("?");
        }
        return "SELECT " + dialect.quote(SyncTable.KEY_COLUMN) + ", " + dialect.quote(table
            .timeColumn()) + " FROM " + dialect.quote(table.tableName()) + " WHERE " + dialect
                .quote(SyncTable.KEY_COLUMN)
            + " IN (" + placeholders + ")";
    }

    /**
     * 生成增量取数语句（按时间与主键排序，供水位线推进）
     *
     * @param dialect 方言
     * @param table   目标表
     * @return 语句
     */
    public static String selectNewerThan(SqlDialect dialect, SyncTable table) {
        return "SELECT " + dialect.quote(SyncTable.KEY_COLUMN) + ", " + dialect.quote(table
            .timeColumn()) + " FROM " + dialect.quote(table.tableName()) + " WHERE "
            + dialect.quote(
                table.timeColumn())
            + " >= ? ORDER BY " + dialect.quote(table.timeColumn())
            + ", " + dialect.quote(SyncTable.KEY_COLUMN);
    }

    /**
     * 生成计数语句
     *
     * @param dialect 方言
     * @param table   目标表
     * @return 语句
     */
    public static String count(SqlDialect dialect, SyncTable table) {
        return "SELECT COUNT(*) FROM " + dialect.quote(table.tableName());
    }
}
