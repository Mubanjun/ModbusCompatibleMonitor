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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 新旧判定与冲突策略的单元测试（FR-SYNC-02/05）。 */
class ConflictResolverTest {

    private static final LocalDateTime OLD = LocalDateTime.of(2026, 9, 23, 10, 0, 0);
    private static final LocalDateTime NEW = LocalDateTime.of(2026, 9, 23, 11, 0, 0);

    private static SyncRecord record(LocalDateTime time) {
        return new SyncRecord("id-1", time, Map.of("DeviceName", "1 号站"));
    }

    @Test
    void insertsWhenTargetMissing() {
        ConflictDecision decision = ConflictResolver.resolve(ConflictPolicy.NEWER_WINS, record(NEW),
            null);
        assertEquals(SyncOutcome.INSERTED, decision.outcome());
        assertTrue(decision.writable());
        assertFalse(decision.conflict());
    }

    @Test
    void newerWinsUpdatesWhenSourceIsNewer() {
        ConflictDecision decision = ConflictResolver.resolve(ConflictPolicy.NEWER_WINS, record(NEW),
            SyncRecord.of("id-1", OLD));
        assertEquals(SyncOutcome.UPDATED, decision.outcome());
        assertTrue(decision.writable());
        assertFalse(decision.conflict());
    }

    @Test
    void newerWinsKeepsTargetWhenTargetIsNewer() {
        ConflictDecision decision = ConflictResolver.resolve(ConflictPolicy.NEWER_WINS, record(OLD),
            SyncRecord.of("id-1", NEW));
        assertEquals(SyncOutcome.KEPT_TARGET, decision.outcome());
        assertFalse(decision.writable());
        assertTrue(decision.conflict());
        assertTrue(decision.reason().contains("目标较新"), decision.reason());
    }

    @Test
    void newerWinsTreatsSameTimeAsSynced() {
        ConflictDecision decision = ConflictResolver.resolve(ConflictPolicy.NEWER_WINS, record(OLD),
            SyncRecord.of("id-1", OLD));
        assertEquals(SyncOutcome.ALREADY_SYNCED, decision.outcome());
        assertFalse(decision.writable());
        assertFalse(decision.conflict(), "重复同步不应产生冲突记录");
        assertTrue(decision.reason().contains("相同"), decision.reason());
    }

    @Test
    void overwriteAlwaysTakesSource() {
        ConflictDecision decision = ConflictResolver.resolve(ConflictPolicy.OVERWRITE, record(OLD),
            SyncRecord.of("id-1", NEW));
        assertEquals(SyncOutcome.UPDATED, decision.outcome());
        assertTrue(decision.writable());
        assertFalse(decision.conflict());
    }

    @Test
    void skipKeepsTargetAndRecordsConflict() {
        ConflictDecision decision = ConflictResolver.resolve(ConflictPolicy.SKIP, record(NEW),
            SyncRecord.of("id-1", OLD));
        assertEquals(SyncOutcome.KEPT_TARGET, decision.outcome());
        assertTrue(decision.conflict());
    }

    @Test
    void manualSuspendsConflict() {
        ConflictDecision decision = ConflictResolver.resolve(ConflictPolicy.MANUAL, record(NEW),
            SyncRecord.of("id-1", OLD));
        assertEquals(SyncOutcome.PENDING_MANUAL, decision.outcome());
        assertFalse(decision.writable());
        assertTrue(decision.conflict());
        assertEquals(NEW, decision.sourceTime());
        assertEquals(OLD, decision.targetTime());
    }

    @Test
    void missingTimeFallsBackToSource() {
        ConflictDecision decision =
            ConflictResolver.resolve(ConflictPolicy.NEWER_WINS, record(null),
                SyncRecord.of("id-1", NEW));
        assertEquals(SyncOutcome.UPDATED, decision.outcome());
        assertTrue(decision.reason().contains("时间缺失"), decision.reason());
    }

    @Test
    void nullPolicyDefaultsToNewerWins() {
        ConflictDecision decision =
            ConflictResolver.resolve(null, record(OLD), SyncRecord.of("id-1",
                NEW));
        assertEquals(SyncOutcome.KEPT_TARGET, decision.outcome());
    }

    @Test
    void parsesPolicyNames() {
        assertEquals(ConflictPolicy.NEWER_WINS, ConflictPolicy.parse("newer_wins"));
        assertEquals(ConflictPolicy.OVERWRITE, ConflictPolicy.parse(" OVERWRITE "));
        assertEquals(ConflictPolicy.MANUAL, ConflictPolicy.parse("manual"));
        assertEquals(ConflictPolicy.NEWER_WINS, ConflictPolicy.parse("unknown"));
        assertEquals(ConflictPolicy.NEWER_WINS, ConflictPolicy.parse(null));
    }
}
