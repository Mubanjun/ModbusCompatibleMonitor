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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SQLite 本地暂存的单元测试（FR-BUF-01/04/05/06）。 */
class SqliteStagingStoreTest {

    @TempDir
    Path tempDir;

    private SqliteStagingStore store;

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    private SqliteStagingStore open(String name) {
        store = new SqliteStagingStore(tempDir.resolve(name).toString());
        store.open();
        return store;
    }

    private static StagingRecord record(long deviceId, int channelNo, double value, long time) {
        return new StagingRecord(0L, 1L, deviceId, "设备" + deviceId, (int) deviceId, channelNo, 2,
            "PH", "pH", "pH", value * 100, value, StagingRecord.QUALITY_GOOD, false, time);
    }

    @Test
    void appendsAndClaimsInWriteOrder() {
        SqliteStagingStore staging = open("a.db");
        assertEquals(3, staging.append(List.of(record(1, 1, 7.42D, 1000L),
            record(1, 2, 19.9D, 1001L), record(2, 1, 1.5D, 1002L))));

        StagingStats stats = staging.stats();
        assertEquals(3L, stats.pendingCount());
        assertEquals(0L, stats.inFlightCount());
        assertEquals(1000L, stats.oldestCollectTime());
        assertEquals(1002L, stats.newestCollectTime());
        assertTrue(stats.fileSizeBytes() > 0L);

        List<StagingRecord> batch = staging.claimBatch(2, "b1");
        assertEquals(2, batch.size());
        assertEquals(7.42D, batch.get(0).engineeringValue(), 1e-9);
        assertEquals(1000L, batch.get(0).collectTime());
        assertEquals(1L, batch.get(0).id());
        assertEquals(1L, staging.stats().pendingCount());
        assertEquals(2L, staging.stats().inFlightCount());
    }

    @Test
    void deletingClaimedRecordsCountsAsFlushed() {
        SqliteStagingStore staging = open("b.db");
        staging.append(List.of(record(1, 1, 7.42D, 1000L), record(1, 2, 19.9D, 1001L)));
        List<StagingRecord> batch = staging.claimBatch(10, "b1");
        assertEquals(2, staging.delete(batch.stream().map(StagingRecord::id).toList()));

        StagingStats stats = staging.stats();
        assertEquals(0L, stats.backlog());
        assertEquals(2L, stats.flushedCount());
        assertEquals(2L, stats.appendedCount());
    }

    @Test
    void releasedBatchBecomesPendingAgain() {
        SqliteStagingStore staging = open("c.db");
        staging.append(List.of(record(1, 1, 7.42D, 1000L)));
        staging.claimBatch(10, "b1");
        assertEquals(1L, staging.stats().inFlightCount());

        assertEquals(1, staging.releaseBatch("b1"));
        StagingStats stats = staging.stats();
        assertEquals(1L, stats.pendingCount());
        assertEquals(0L, stats.inFlightCount());
    }

    @Test
    void reopeningRecoversInFlightRecords() {
        SqliteStagingStore staging = open("d.db");
        staging.append(List.of(record(1, 1, 7.42D, 1000L), record(1, 2, 19.9D, 1001L)));
        staging.claimBatch(10, "b1");
        assertEquals(2L, staging.stats().inFlightCount());
        staging.close();

        // 模拟进程异常退出后重启：遗留的「上传中」记录必须退回待上传
        store = new SqliteStagingStore(tempDir.resolve("d.db").toString());
        store.open();
        assertEquals(2L, store.stats().pendingCount());
        assertEquals(0L, store.stats().inFlightCount());
    }

    @Test
    void purgesOldestRecordsWhenOverCapacity() {
        SqliteStagingStore staging = open("e.db");
        for (int i = 0; i < 5; i++) {
            staging.append(List.of(record(1, 1, i, 1000L + i)));
        }
        assertEquals(2, staging.purgeOverflow(3));
        StagingStats stats = staging.stats();
        assertEquals(3L, stats.pendingCount());
        assertEquals(2L, stats.droppedCount());
        // 保留的是最新的 3 条
        List<StagingRecord> batch = staging.claimBatch(10, "b1");
        assertEquals(1002L, batch.get(0).collectTime());
        assertEquals(1004L, batch.get(2).collectTime());
    }

    @Test
    void purgeDoesNothingBelowCapacity() {
        SqliteStagingStore staging = open("f.db");
        staging.append(List.of(record(1, 1, 1D, 1000L)));
        assertEquals(0, staging.purgeOverflow(10));
    }

    @Test
    void rejectsUseBeforeOpen() {
        store = new SqliteStagingStore(tempDir.resolve("g.db").toString());
        assertFalse(store.isOpen());
        assertThrows(IllegalStateException.class,
            () -> store.append(List.of(record(1, 1, 1D, 1000L))));
        assertThrows(IllegalStateException.class, () -> store.claimBatch(1, "b"));
    }

    @Test
    void emptyAppendAndDeleteAreNoOps() {
        SqliteStagingStore staging = open("h.db");
        assertEquals(0, staging.append(List.of()));
        assertEquals(0, staging.delete(List.of()));
        assertTrue(staging.isOpen());
    }
}
