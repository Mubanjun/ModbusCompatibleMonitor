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

import com.mbj.jdcompatiblemonitor.report.SqlDialect;
import com.mbj.jdcompatiblemonitor.sync.SyncRecord;
import com.mbj.jdcompatiblemonitor.sync.SyncTable;
import com.mbj.jdcompatiblemonitor.sync.UpsertStatement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 同步目标写入（FR-SYNC-01/03）：按方言幂等 upsert，并支持存在性查询与计数。
 *
 * <p>MySQL/MariaDB/PostgreSQL/KingbaseES 走单语句原生 upsert；
 * SQL Server/Oracle/达梦 无统一语法，退化为「按主键存在则更新、否则插入」。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
public class SyncTargetWriter {

    /** 单条 IN 查询的主键上限 */
    private static final int ID_CHUNK = 500;

    private final DataSource dataSource;

    public SyncTargetWriter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 目标库连接参数
     *
     * @param dialect  方言
     * @param url      JDBC 地址
     * @param username 用户名
     * @param password 口令
     */
    public record TargetConnection(SqlDialect dialect, String url, String username,
        String password) {
    }

    /**
     * 查询目标库中已存在的主键与记录时间
     *
     * @param target 目标连接
     * @param table  目标表
     * @param ids    主键列表
     * @return 主键到记录时间的映射
     */
    public Map<String, LocalDateTime> selectExisting(TargetConnection target, SyncTable table,
        List<String> ids) {
        Map<String, LocalDateTime> existing = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return existing;
        }
        try (Connection connection = this.connect(target)) {
            List<String> unique = new ArrayList<>(new LinkedHashSet<>(ids));
            for (int start = 0; start < unique.size(); start += ID_CHUNK) {
                List<String> chunk =
                    unique.subList(start, Math.min(start + ID_CHUNK, unique.size()));
                String sql = UpsertStatement.selectByIds(target.dialect(), table, chunk.size());
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    int index = 1;
                    for (String id : chunk) {
                        statement.setString(index++, id);
                    }
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            Timestamp timestamp = rs.getTimestamp(2);
                            existing.put(rs.getString(1), timestamp == null ? null : timestamp
                                .toLocalDateTime());
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("查询目标库已有记录失败（" + table.tableName() + "）：" + e
                .getMessage(), e);
        }
        return existing;
    }

    /**
     * 幂等写入目标库
     *
     * @param target      目标连接
     * @param table       目标表
     * @param records     待写入记录
     * @param existingIds 目标库已存在的主键（非原生 upsert 方言用于区分新增与更新）
     * @return 写入条数
     */
    public int upsert(TargetConnection target, SyncTable table, List<SyncRecord> records,
        Set<String> existingIds) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        try (Connection connection = this.connect(target)) {
            if (UpsertStatement.supportsNativeUpsert(target.dialect())) {
                return this.nativeUpsert(connection, target, table, records);
            }
            return this.fallbackWrite(connection, target, table, records, existingIds);
        } catch (Exception e) {
            throw new IllegalStateException("写入目标库失败（" + table.tableName() + "）：" + e
                .getMessage(), e);
        }
    }

    /**
     * 统计目标库行数
     *
     * @param target 目标连接
     * @param table  目标表
     * @return 行数
     */
    public long count(TargetConnection target, SyncTable table) {
        try (Connection connection = this.connect(target);
            PreparedStatement statement = connection.prepareStatement(UpsertStatement.count(target
                .dialect(), table));
            ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (Exception e) {
            throw new IllegalStateException("统计目标库行数失败（" + table.tableName() + "）：" + e
                .getMessage(), e);
        }
    }

    private int nativeUpsert(Connection connection, TargetConnection target, SyncTable table,
        List<SyncRecord> records) throws Exception {
        String sql = UpsertStatement.nativeUpsert(target.dialect(), table);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (SyncRecord record : records) {
                this.bind(statement, record, table, true);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (Exception batchError) {
            log.warn("目标表 [{}] 整批写入失败，退化为逐条写入：{}", table.tableName(), batchError
                .getMessage());
            return this.writeOneByOne(connection, target, table, records);
        }
        return records.size();
    }

    /**
     * 整批写入失败后退化为逐条写入。
     *
     * <p>批量 upsert 是一条语句：只要有一条记录超长/脏数据，严格模式下整批都会失败，
     * 而调用方会带着异常退出、水位线不推进 —— 任务会永远卡在同一批上。这里把"整批失败"
     * 降级为"个别记录失败"（失败的记录打日志，成功的照常写入），保证同步能继续推进。</p>
     *
     * @param connection 连接
     * @param target     目标连接
     * @param table      目标表
     * @param records    待写入记录
     * @return 实际写入条数
     * @throws Exception 一条都写不进去时抛出（说明目标库真的不可用）
     */
    private int writeOneByOne(Connection connection, TargetConnection target, SyncTable table,
        List<SyncRecord> records) throws Exception {
        String sql = UpsertStatement.nativeUpsert(target.dialect(), table);
        int written = 0;
        int failed = 0;
        Exception last = null;
        for (SyncRecord record : records) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                this.bind(statement, record, table, true);
                statement.executeUpdate();
                written++;
            } catch (Exception e) {
                failed++;
                last = e;
                log.warn("目标表 [{}] 记录 [{}] 写入失败（跳过该条，继续同步）：{}", table.tableName(),
                    record.id(), e.getMessage());
            }
        }
        if (written == 0 && last != null) {
            throw last;
        }
        if (failed > 0) {
            log.warn("目标表 [{}] 本批 {} 条中有 {} 条写入失败（已跳过，详见上方日志）", table.tableName(),
                records.size(), failed);
        }
        return written;
    }

    private int fallbackWrite(Connection connection, TargetConnection target, SyncTable table,
        List<SyncRecord> records, Set<String> existingIds) throws Exception {
        String insertSql = UpsertStatement.insert(target.dialect(), table);
        String updateSql = UpsertStatement.updateById(target.dialect(), table);
        Set<String> existing = existingIds == null ? Set.of() : existingIds;
        int written = 0;
        for (SyncRecord record : records) {
            boolean update = existing.contains(record.id());
            try (PreparedStatement statement = connection.prepareStatement(update ? updateSql
                : insertSql)) {
                if (update) {
                    int index = 1;
                    for (String column : table.valueColumns()) {
                        statement.setObject(index++,
                            table.clamp(column, record.values().get(column)));
                    }
                    statement.setString(index,
                        (String) table.clamp(SyncTable.KEY_COLUMN, record.id()));
                } else {
                    this.bind(statement, record, table, true);
                }
                statement.executeUpdate();
                written++;
            }
        }
        return written;
    }

    /**
     * 绑定插入参数
     *
     * @param statement 语句
     * @param record    记录
     * @param table     目标表
     * @param withKey   是否包含主键（插入需要，更新放末尾）
     */
    private void bind(PreparedStatement statement, SyncRecord record, SyncTable table,
        boolean withKey) throws Exception {
        int index = 1;
        if (withKey) {
            statement.setString(index++, (String) table.clamp(SyncTable.KEY_COLUMN, record.id()));
        }
        for (String column : table.valueColumns()) {
            statement.setObject(index++, table.clamp(column, record.values().get(column)));
        }
    }

    private Connection connect(TargetConnection target) throws Exception {
        try {
            Class.forName(target.dialect().driverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("目标库驱动未提供：" + target.dialect().driverClass());
        }
        return DriverManager.getConnection(target.url(), target.username(), target.password());
    }

    /**
     * 生成逗号分隔的字符串（用于日志）
     *
     * @param values 值集合
     * @return 文本
     */
    static String join(Set<String> values) {
        StringJoiner joiner = new StringJoiner(",");
        values.forEach(joiner::add);
        return joiner.toString();
    }
}
