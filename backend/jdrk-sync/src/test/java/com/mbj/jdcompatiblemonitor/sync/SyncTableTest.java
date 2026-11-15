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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** 目标列宽截断的单元测试（FR-SYNC-01：超长值不能让整批写入失败）。 */
class SyncTableTest {

    @Test
    void truncatesOverlongStringsToTargetColumnWidth() {
        String longUrl = "x".repeat(600);
        assertEquals(255, ((String) SyncTable.TBSYSLOG.clamp("Detail", longUrl)).length());
        assertEquals(255, ((String) SyncTable.TBSYSLOG.clamp("Result", longUrl)).length());
        // 不同表的同名列列宽不同：tbhistory.ID 64、tbsyslog.ID 32
        assertEquals(64, ((String) SyncTable.TBHISTORY.clamp("ID", "y".repeat(100))).length());
        assertEquals(32, ((String) SyncTable.TBSYSLOG.clamp("ID", "z".repeat(100))).length());
    }

    @Test
    void keepsShortValuesAndNonStringsUntouched() {
        assertEquals("成功", SyncTable.TBSYSLOG.clamp("Result", "成功"));
        assertEquals(Integer.valueOf(42), SyncTable.TBHISTORY.clamp("Tem", 42));
        assertNull(SyncTable.TBSYSLOG.clamp("Detail", null));
        // 未登记列宽的列不截断
        String unknown = "x".repeat(300);
        assertEquals(unknown, SyncTable.TBSYSLOG.clamp("Unknown", unknown));
    }
}
