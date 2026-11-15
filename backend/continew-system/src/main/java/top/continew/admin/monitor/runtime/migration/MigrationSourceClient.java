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

package top.continew.admin.monitor.runtime.migration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 源库（仁科原库）只读客户端（FR-IO-06 检测原库）。
 *
 * <p>按向导传入的参数动态建连，只做元数据与数据读取；不使用连接池，
 * 每次调用现开现关，避免把现场库连接长期挂在平台里。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
public final class MigrationSourceClient {

    /** 允许读取的最大行数（防止误操作拉爆内存） */
    public static final int MAX_ROWS = 500_000;

    /** 查询超时（秒） */
    private static final int QUERY_TIMEOUT_SECONDS = 60;

    private final DriverManagerDataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造源库客户端
     *
     * @param spec                 连接参数
     * @param connectTimeoutSeconds 连接超时（秒）
     */
    public MigrationSourceClient(MigrationSpec spec, int connectTimeoutSeconds) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(spec.jdbcUrl(connectTimeoutSeconds));
        dataSource.setUsername(spec.username());
        dataSource.setPassword(spec.password());
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
    }

    /**
     * JDBC 模板
     *
     * @return 模板
     */
    public JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    /**
     * 当前库名
     *
     * @return 库名
     */
    public String database() {
        return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
    }

    /**
     * 列出全部表
     *
     * @return 表名
     */
    public List<String> listTables() {
        return jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "ORDER BY table_name",
            String.class);
    }

    /**
     * 列出表的列名
     *
     * @param table 表名
     * @return 列名
     */
    public List<String> columns(String table) {
        return jdbcTemplate.queryForList("SELECT column_name FROM information_schema.columns WHERE "
            + "table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position",
            String.class,
            table);
    }

    /**
     * 是否存在指定表
     *
     * @param table 表名
     * @return 是否存在
     */
    public boolean hasTable(String table) {
        Integer count =
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = ?", Integer.class, table);
        return count != null && count > 0;
    }

    /**
     * 统计表行数
     *
     * @param table 表名
     * @return 行数
     */
    public long count(String table) {
        Long value =
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + escape(table), Long.class);
        return value == null ? 0L : value;
    }

    /**
     * 在**同一条连接**上统计多张表的行数（探测用）
     *
     * <p>源库表多时，按表逐个查询会逐表建连；这里一次建连、逐表 COUNT，
     * 建连次数从「表数」降到 1。</p>
     *
     * @param tables 表名
     * @return 表名到行数（保持传入顺序）
     */
    public Map<String, Long> countAll(List<String> tables) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (tables == null || tables.isEmpty()) {
            return counts;
        }
        try (Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            for (String table : tables) {
                try (ResultSet result =
                    statement.executeQuery("SELECT COUNT(*) FROM " + escape(table))) {
                    counts.put(table, result.next() ? result.getLong(1) : 0L);
                }
            }
            return counts;
        } catch (SQLException e) {
            throw new IllegalStateException("统计源表行数失败：" + e.getMessage(), e);
        }
    }

    /**
     * 在同一条连接上取回全部表的列名（探测用）
     *
     * @return 表名到列名（按 ordinal_position 排序）
     */
    public Map<String, List<String>> columnsAll() {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(
                "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = "
                    + "DATABASE() ORDER BY table_name, ordinal_position")) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    columns.computeIfAbsent(result.getString(1), key -> new ArrayList<>())
                        .add(result
                            .getString(2));
                }
            }
            return columns;
        } catch (SQLException e) {
            throw new IllegalStateException("读取源表结构失败：" + e.getMessage(), e);
        }
    }

    /**
     * 标识符转义（仅允许字母数字下划线，规避拼接注入）
     *
     * @param name 标识符
     * @return 转义结果
     */
    public static String escape(String name) {
        if (name == null || !name.matches("^[A-Za-z0-9_]+$")) {
            throw new IllegalArgumentException("非法标识符：" + name);
        }
        return "`" + name + "`";
    }
}
