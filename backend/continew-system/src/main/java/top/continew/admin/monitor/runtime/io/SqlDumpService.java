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

package top.continew.admin.monitor.runtime.io;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.continew.starter.core.util.validation.CheckUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 业务表逻辑导出与回灌（FR-IO-02/03/04）。
 *
 * <p>用「分页 SELECT + INSERT 语句」而不是依赖驱动游标：跨方言一致、内存可控，
 * 千万级表导出只需固定大小的分页缓冲。回灌时只允许 `INSERT INTO \`biz_*\`` 与
 * `TRUNCATE TABLE \`biz_*\``，避免备份包被当成任意 SQL 执行入口。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlDumpService {

    /** 分页抓取大小（内存上限 ≈ 页大小 × 单行大小） */
    public static final int PAGE_SIZE = 2000;

    /** 允许导出的表名前缀 */
    public static final String TABLE_PREFIX = "biz_";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSS");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    /**
     * 列出业务表
     *
     * @return 表名列表
     */
    public List<String> listTables() {
        return jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name LIKE '" + TABLE_PREFIX + "%' ORDER BY table_name",
            String.class);
    }

    /**
     * 校验表名（仅允许业务表前缀）
     *
     * @param table 表名
     * @return 表名
     */
    public String requireTable(String table) {
        CheckUtils.throwIf(table == null || table.isBlank(), "表名不能为空");
        String name = table.trim().toLowerCase(Locale.ROOT);
        CheckUtils.throwIf(!name.startsWith(TABLE_PREFIX), "只允许操作 {} 前缀的业务表", TABLE_PREFIX);
        CheckUtils.throwIf(!name.matches("^[a-z0-9_]+$"), "非法表名：{}", table);
        CheckUtils.throwIf(!this.listTables().contains(name), "表不存在：{}", table);
        return name;
    }

    /**
     * 统计行数
     *
     * @param table 表名
     * @return 行数
     */
    public long count(String table) {
        String name = this.requireTable(table);
        Long value =
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + escape(name), Long.class);
        return value == null ? 0L : value;
    }

    /**
     * 把单表导出为 INSERT 语句
     *
     * @param table  表名
     * @param writer 输出
     * @throws IOException 写出失败
     */
    public void dump(String table, Writer writer) throws IOException {
        String name = this.requireTable(table);
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("SHOW COLUMNS FROM " + escape(
            name));
        List<String> names = new ArrayList<>(columns.size());
        String primaryKey = null;
        for (Map<String, Object> column : columns) {
            String field = String.valueOf(column.get("Field"));
            names.add(field);
            if (primaryKey == null && "PRI".equalsIgnoreCase(String.valueOf(column.get("Key")))) {
                primaryKey = field;
            }
        }
        writer.write("-- table " + name + "\n");
        writer.write("DELETE FROM " + escape(name) + ";\n");
        long rows = 0L;
        if (primaryKey == null) {
            // 无主键的表只能回退到 LIMIT/OFFSET 分页
            rows = this.dumpByOffset(name, names, writer);
        } else {
            // 键集分页：比 LIMIT/OFFSET 更省资源，导出期间有并发写入也不会跳行/重复
            String ordered = "SELECT " + joinColumns(names) + " FROM " + escape(name) + " ORDER BY "
                + escape(primaryKey) + " LIMIT ?";
            String keyset = "SELECT " + joinColumns(names) + " FROM " + escape(name) + " WHERE "
                + escape(primaryKey) + " > ? ORDER BY " + escape(primaryKey) + " LIMIT ?";
            Object cursor = null;
            while (true) {
                List<Map<String, Object>> page = cursor == null ? jdbcTemplate.queryForList(ordered,
                    PAGE_SIZE) : jdbcTemplate.queryForList(keyset, cursor, PAGE_SIZE);
                if (page.isEmpty()) {
                    break;
                }
                this.writeRows(name, names, page, writer);
                rows += page.size();
                cursor = page.get(page.size() - 1).get(primaryKey);
                if (page.size() < PAGE_SIZE) {
                    break;
                }
            }
        }
        writer.write("-- rows " + rows + "\n");
        writer.flush();
    }

    private long dumpByOffset(String name, List<String> names, Writer writer) throws IOException {
        String select =
            "SELECT " + joinColumns(names) + " FROM " + escape(name) + " LIMIT ? OFFSET ?";
        int offset = 0;
        long rows = 0L;
        while (true) {
            List<Map<String, Object>> page = jdbcTemplate.queryForList(select, PAGE_SIZE, offset);
            if (page.isEmpty()) {
                break;
            }
            this.writeRows(name, names, page, writer);
            rows += page.size();
            offset += page.size();
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        return rows;
    }

    private void writeRows(String name, List<String> names, List<Map<String, Object>> page,
        Writer writer) throws IOException {
        for (Map<String, Object> row : page) {
            writer.write("INSERT INTO " + escape(name) + " (" + joinColumns(names) + ") VALUES (");
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    writer.write(", ");
                }
                writer.write(literal(row.get(names.get(i))));
            }
            writer.write(");\n");
        }
    }

    /**
     * 从备份包中的脚本回灌（只允许业务表的 INSERT / DELETE / TRUNCATE）
     *
     * @param table      目标表
     * @param reader     脚本
     * @param override   是否先清空
     * @return 执行语句数
     * @throws IOException 读取失败
     */
    public int restore(String table, BufferedReader reader, boolean override) throws IOException {
        String name = this.requireTable(table);
        List<String> statements = new ArrayList<>();
        int total = 0;
        if (override) {
            statements.add("DELETE FROM " + escape(name));
        }
        StringBuilder pending = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (pending.length() == 0 && (trimmed.isEmpty() || trimmed.startsWith("--"))) {
                continue;
            }
            pending.append(trimmed);
            if (!trimmed.endsWith(";")) {
                // 兼容跨行语句（历史字符串值里可能原本含换行）
                continue;
            }
            String statement = pending.toString();
            pending.setLength(0);
            String lower = statement.toLowerCase(Locale.ROOT);
            boolean allowed = lower.startsWith("insert into " + escape(name) + " ")
                || lower.startsWith("delete from " + escape(name))
                || lower.startsWith("truncate table " + escape(name));
            CheckUtils.throwIf(!allowed, "备份脚本包含不允许的语句（仅允许业务表增删）：{}", statement
                .length() > 80 ? statement.substring(0, 80) : statement);
            statements.add(statement);
            if (statements.size() >= 500) {
                this.execute(statements);
                total += statements.size();
                statements.clear();
            }
        }
        CheckUtils.throwIf(pending.length() > 0, "备份脚本最后一条语句不完整");
        if (!statements.isEmpty()) {
            this.execute(statements);
            total += statements.size();
        }
        return total;
    }

    /**
     * 逐条执行恢复语句。
     *
     * <p>刻意不用 {@code batchUpdate}：连接串开启了 {@code rewriteBatchedStatements=true}，
     * 驱动会把不能合并成多值 INSERT 的语句拼成多语句发送，而连接串未开启
     * {@code allowMultiQueries}，MySQL 会直接报语法错误（实测恢复报警通知表时踩到）。</p>
     *
     * @param statements 语句列表
     */
    private void execute(List<String> statements) {
        for (String statement : statements) {
            String sql = statement.endsWith(";") ? statement.substring(0, statement.length() - 1)
                : statement;
            jdbcTemplate.update(sql);
        }
    }

    private static String joinColumns(List<String> names) {
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(escape(name));
        }
        return builder.toString();
    }

    private static String escape(String name) {
        return "`" + name + "`";
    }

    /**
     * 把 Java 值转成 SQL 字面量
     *
     * @param value 值
     * @return 字面量
     */
    public static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number number) {
            if (number instanceof BigDecimal decimal) {
                return decimal.toPlainString();
            }
            if (number instanceof BigInteger integer) {
                return integer.toString();
            }
            return number.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        if (value instanceof byte[] bytes) {
            return "X'" + toHex(bytes) + "'";
        }
        if (value instanceof Timestamp timestamp) {
            return quote(timestamp.toLocalDateTime().format(DATE_TIME));
        }
        if (value instanceof LocalDateTime dateTime) {
            return quote(dateTime.format(DATE_TIME));
        }
        if (value instanceof LocalDate date) {
            return quote(date.format(DATE));
        }
        if (value instanceof LocalTime time) {
            return quote(time.format(TIME));
        }
        if (value instanceof Date date) {
            return quote(new Timestamp(date.getTime()).toLocalDateTime().format(DATE_TIME));
        }
        return quote(value.toString());
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('\'');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\'' || current == '\\') {
                builder.append('\\').append(current);
            } else if (current == '\n') {
                builder.append("\\n");
            } else if (current == '\r') {
                builder.append("\\r");
            } else if (current == '\t') {
                builder.append("\\t");
            } else {
                builder.append(current);
            }
        }
        builder.append('\'');
        return builder.toString();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(Character.forDigit((current >> 4) & 0xF, 16));
            builder.append(Character.forDigit(current & 0xF, 16));
        }
        return builder.toString().toUpperCase(Locale.ROOT);
    }
}
