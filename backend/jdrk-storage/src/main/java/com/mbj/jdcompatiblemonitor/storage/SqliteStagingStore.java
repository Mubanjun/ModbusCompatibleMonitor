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

package com.mbj.jdcompatiblemonitor.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 SQLite（WAL）的本地暂存实现（FR-BUF-01/04/05）。
 *
 * <p>对应 DR-01 的 {@code t_sample_buffer}：采集写入与上传读取分离，
 * 写入使用单事务批量提交，关键查询走 {@code (upload_state, id)} 与 {@code collect_time} 索引。</p>
 *
 * <p>崩溃恢复：打开时把上次进程遗留的「上传中」记录统一退回待上传，
 * 因此强杀进程不会丢批次；已上传成功的数据由业务库的来源主键去重，不会重复入库。</p>
 */
public final class SqliteStagingStore implements StagingStore {

    /** 待上传 */
    private static final int STATE_PENDING = 0;
    /** 上传中 */
    private static final int STATE_IN_FLIGHT = 1;

    private final String databasePath;
    private final ReentrantLock lock = new ReentrantLock();

    private Connection connection;
    private volatile boolean open;
    private final AtomicLong appended = new AtomicLong(0L);
    private final AtomicLong flushed = new AtomicLong(0L);
    private final AtomicLong dropped = new AtomicLong(0L);

    public SqliteStagingStore(String databasePath) {
        this.databasePath = databasePath;
    }

    @Override
    public void open() {
        lock.lock();
        try {
            if (open) {
                return;
            }
            File file = new File(databasePath);
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw new IllegalStateException("无法创建暂存目录：" + parent);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute(this.ddl());
                statement.execute("CREATE INDEX IF NOT EXISTS idx_sample_state"
                    + " ON t_sample_buffer(upload_state, id)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_sample_time"
                    + " ON t_sample_buffer(collect_time)");
            }
            open = true;
            this.releaseOrphans();
        } catch (SQLException e) {
            throw new IllegalStateException("打开本地暂存库失败：" + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public int append(Collection<StagingRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        lock.lock();
        try {
            this.requireOpen();
            String sql =
                "INSERT INTO t_sample_buffer (port_id, device_id, device_name, device_addr,"
                    + " channel_no, analog_no, element_type, element_name, unit, raw_value, eng_value,"
                    + " quality, over_range, collect_time, upload_state, create_time)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + STATE_PENDING
                    + ", ?)";
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (StagingRecord record : records) {
                    statement.setLong(1, record.portId());
                    statement.setLong(2, record.deviceId());
                    statement.setString(3, record.deviceName());
                    statement.setInt(4, record.deviceAddr());
                    statement.setInt(5, record.channelNo());
                    statement.setInt(6, record.analogNo());
                    statement.setString(7, record.elementType());
                    statement.setString(8, record.elementName());
                    statement.setString(9, record.unit());
                    statement.setDouble(10, record.rawValue());
                    statement.setDouble(11, record.engineeringValue());
                    statement.setString(12, record.quality());
                    statement.setInt(13, record.overRange() ? 1 : 0);
                    statement.setLong(14, record.collectTime());
                    statement.setLong(15, now);
                    statement.addBatch();
                }
                int[] counts = statement.executeBatch();
                int total = 0;
                for (int count : counts) {
                    total += Math.max(count, 0);
                }
                appended.addAndGet(total);
                return total;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("写入本地暂存失败：" + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<StagingRecord> claimBatch(int limit, String batchId) {
        lock.lock();
        try {
            this.requireOpen();
            List<StagingRecord> records = new ArrayList<>();
            String select = "SELECT id, port_id, device_id, device_name, device_addr, channel_no,"
                + " analog_no, element_type, element_name, unit, raw_value, eng_value, quality,"
                + " over_range, collect_time FROM t_sample_buffer WHERE upload_state = "
                + STATE_PENDING + " ORDER BY id LIMIT ?";
            try (PreparedStatement statement = connection.prepareStatement(select)) {
                statement.setInt(1, Math.max(limit, 1));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        records.add(new StagingRecord(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                            rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7),
                            rs.getString(8), rs.getString(9), rs.getString(10), rs.getDouble(11),
                            rs.getDouble(12), rs.getString(13), rs.getInt(14) != 0,
                            rs.getLong(15)));
                    }
                }
            }
            if (records.isEmpty()) {
                return records;
            }
            String update = "UPDATE t_sample_buffer SET upload_state = " + STATE_IN_FLIGHT
                + ", batch_id = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                for (StagingRecord record : records) {
                    statement.setString(1, batchId);
                    statement.setLong(2, record.id());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return records;
        } catch (SQLException e) {
            throw new IllegalStateException("读取本地暂存失败：" + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int delete(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        lock.lock();
        try {
            this.requireOpen();
            String sql = "DELETE FROM t_sample_buffer WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Long id : ids) {
                    statement.setLong(1, id);
                    statement.addBatch();
                }
                int[] counts = statement.executeBatch();
                int total = 0;
                for (int count : counts) {
                    total += Math.max(count, 0);
                }
                flushed.addAndGet(total);
                return total;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("清理本地暂存失败：" + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int releaseBatch(String batchId) {
        lock.lock();
        try {
            this.requireOpen();
            String sql = "UPDATE t_sample_buffer SET upload_state = " + STATE_PENDING
                + ", batch_id = NULL WHERE batch_id = ? AND upload_state = " + STATE_IN_FLIGHT;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, batchId);
                return statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("回退本地暂存批次失败：" + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int purgeOverflow(long maxRows) {
        lock.lock();
        try {
            this.requireOpen();
            long count = this.count();
            if (count <= maxRows) {
                return 0;
            }
            long exceed = count - maxRows;
            String sql = "DELETE FROM t_sample_buffer WHERE id IN (SELECT id FROM t_sample_buffer"
                + " ORDER BY id LIMIT ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, exceed);
                int deleted = statement.executeUpdate();
                dropped.addAndGet(deleted);
                return deleted;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("滚动清理本地暂存失败：" + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public StagingStats stats() {
        lock.lock();
        try {
            if (!open) {
                return new StagingStats(0, 0, 0, 0, appended.get(), flushed.get(), dropped.get(),
                    0);
            }
            long pending = this.countByState(STATE_PENDING);
            long inFlight = this.countByState(STATE_IN_FLIGHT);
            long oldest = this.timeByOrder("MIN(collect_time)");
            long newest = this.timeByOrder("MAX(collect_time)");
            File file = new File(databasePath);
            return new StagingStats(pending, inFlight, oldest, newest, appended.get(),
                flushed.get(), dropped.get(), file.exists() ? file.length() : 0L);
        } catch (SQLException e) {
            throw new IllegalStateException("读取本地暂存状态失败：" + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            open = false;
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (SQLException ignored) {
            // 关闭失败无需处理
        } finally {
            lock.unlock();
        }
    }

    /** 把上次进程遗留的「上传中」记录退回待上传（FR-BUF-05）。 */
    private void releaseOrphans() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE t_sample_buffer SET upload_state = " + STATE_PENDING
                + ", batch_id = NULL WHERE upload_state = " + STATE_IN_FLIGHT);
        }
    }

    /** 统计指定状态的条数。 */
    private long countByState(int state) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM t_sample_buffer WHERE upload_state = ?")) {
            statement.setInt(1, state);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** 统计总条数。 */
    private long count() throws SQLException {
        try (Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM t_sample_buffer")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /** 取时间聚合值。 */
    private long timeByOrder(String aggregate) throws SQLException {
        try (Statement statement = connection.createStatement();
            ResultSet rs =
                statement.executeQuery("SELECT " + aggregate + " FROM t_sample_buffer")) {
            if (rs.next()) {
                long value = rs.getLong(1);
                return rs.wasNull() ? 0L : value;
            }
            return 0L;
        }
    }

    /** 校验已打开。 */
    private void requireOpen() {
        if (!open || connection == null) {
            throw new IllegalStateException("本地暂存库尚未打开");
        }
    }

    /** 建表语句（DR-01 的 t_sample_buffer）。 */
    private String ddl() {
        return "CREATE TABLE IF NOT EXISTS t_sample_buffer ("
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " port_id INTEGER NOT NULL DEFAULT 0,"
            + " device_id INTEGER NOT NULL DEFAULT 0,"
            + " device_name TEXT,"
            + " device_addr INTEGER,"
            + " channel_no INTEGER,"
            + " analog_no INTEGER,"
            + " element_type TEXT,"
            + " element_name TEXT,"
            + " unit TEXT,"
            + " raw_value REAL,"
            + " eng_value REAL,"
            + " quality TEXT NOT NULL DEFAULT 'GOOD',"
            + " over_range INTEGER NOT NULL DEFAULT 0,"
            + " collect_time INTEGER NOT NULL,"
            + " upload_state INTEGER NOT NULL DEFAULT 0,"
            + " batch_id TEXT,"
            + " create_time INTEGER NOT NULL)";
    }
}
