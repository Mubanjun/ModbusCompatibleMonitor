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

package top.continew.admin.monitor;

import com.mbj.jdcompatiblemonitor.protocol.transport.SerialTransport;
import org.junit.jupiter.api.Test;
import top.continew.admin.monitor.runtime.buffer.DiskSpaceGuard;
import top.continew.admin.monitor.runtime.io.ImportService;
import top.continew.admin.monitor.runtime.io.ImportType;
import top.continew.admin.monitor.runtime.io.SqlDumpService;
import top.continew.admin.monitor.runtime.io.TabularReader;
import top.continew.admin.monitor.runtime.migration.MigrationMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 监控业务域纯逻辑单测（不依赖 Spring 与数据库）。
 *
 * <p>覆盖本轮新增的导出字面量转义、CSV 解析、导入幂等标识、迁移模式与串口帧间静默配置，
 * 这些都是「错了很难在现场发现」的细节。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
class MonitorPureLogicTest {

    @Test
    void literalShouldEscapeSqlValues() {
        assertEquals("NULL", SqlDumpService.literal(null));
        assertEquals("1", SqlDumpService.literal(1));
        assertEquals("1.50", SqlDumpService.literal(new BigDecimal("1.50")));
        assertEquals("1", SqlDumpService.literal(Boolean.TRUE));
        assertEquals("0", SqlDumpService.literal(Boolean.FALSE));
        assertEquals("X'0AFF'", SqlDumpService.literal(new byte[] {0x0A, (byte) 0xFF}));
        // 引号与反斜杠按 MySQL 反斜杠转义（与 dump 生成的回灌脚本一致）
        assertEquals("'O\\'Brien'", SqlDumpService.literal("O'Brien"));
        assertEquals("'a\\\\b'", SqlDumpService.literal("a\\b"));
    }

    @Test
    void literalShouldKeepStatementsOnOneLine() {
        String escaped =
            SqlDumpService.literal("第一行" + (char) 10 + "第二行" + (char) 13 + (char) 9 + "尾");
        assertTrue(escaped.indexOf((char) 10) < 0, "转义后不能含真实换行：" + escaped);
        assertTrue(escaped.indexOf((char) 13) < 0, "转义后不能含真实回车：" + escaped);
        assertTrue(escaped.contains("\\n"), "换行应转义为 \\n：" + escaped);
    }

    @Test
    void parseCsvLineShouldHandleQuotesAndCommas() {
        List<String> fields = TabularReader.parseCsvLine("a,b,\"c,d\",\"e\"\"f\"");
        assertEquals(List.of("a", "b", "c,d", "e\"f"), fields);
        assertEquals(List.of("x"), TabularReader.parseCsvLine("x"));
        assertEquals(List.of("", ""), TabularReader.parseCsvLine(","));
    }

    @Test
    void sourceIdShouldBeStableAndDistinct() {
        LocalDateTime time = LocalDateTime.of(2026, 9, 24, 12, 0, 0);
        long first = ImportService.sourceId(1L, 1, 2, time);
        assertEquals(first, ImportService.sourceId(1L, 1, 2, time), "同一输入必须得到同一标识（幂等）");
        assertNotEquals(first, ImportService.sourceId(1L, 1, 1, time));
        assertNotEquals(first, ImportService.sourceId(1L, 2, 2, time));
        assertNotEquals(first, ImportService.sourceId(2L, 1, 2, time));
        assertTrue(first >= 0L, "来源标识必须非负（biz_data.source_id 为 bigint）");
    }

    @Test
    void importTypeShouldRejectUnknownValues() {
        assertEquals(ImportType.HISTORY, ImportType.of("history"));
        assertEquals(ImportType.CONFIG, ImportType.of(" CONFIG "));
        assertThrows(IllegalArgumentException.class, () -> ImportType.of("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> ImportType.of(null));
    }

    @Test
    void migrationModeShouldRejectUnknownValues() {
        assertEquals(MigrationMode.COEXIST, MigrationMode.of("coexist"));
        assertEquals(MigrationMode.OFF, MigrationMode.of(null));
        assertThrows(IllegalArgumentException.class, () -> MigrationMode.of("SWITCHING"));
    }

    @Test
    void serialFrameGapShouldBeClamped() {
        int original = SerialTransport.frameGapMillis();
        try {
            SerialTransport.setFrameGapMillis(20);
            assertEquals(20, SerialTransport.frameGapMillis());
            SerialTransport.setFrameGapMillis(-5);
            assertEquals(0, SerialTransport.frameGapMillis());
            SerialTransport.setFrameGapMillis(99_999);
            assertEquals(1000, SerialTransport.frameGapMillis());
        } finally {
            SerialTransport.setFrameGapMillis(original);
        }
    }

    @Test
    void diskSpaceGuardShouldDecideByThresholds() {
        assertEquals(DiskSpaceGuard.Level.PROTECT, DiskSpaceGuard.decide(100L, 512L, 128L));
        // 恰好等于阈值按「不够用」处理，避免边界上漏掉告警
        assertEquals(DiskSpaceGuard.Level.PROTECT, DiskSpaceGuard.decide(128L, 512L, 128L));
        assertEquals(DiskSpaceGuard.Level.WARN, DiskSpaceGuard.decide(300L, 512L, 128L));
        assertEquals(DiskSpaceGuard.Level.WARN, DiskSpaceGuard.decide(512L, 512L, 128L));
        assertEquals(DiskSpaceGuard.Level.OK, DiskSpaceGuard.decide(4096L, 512L, 128L));
        // 取不到剩余空间时按正常处理（Long.MAX_VALUE），不能误报
        assertEquals(DiskSpaceGuard.Level.OK, DiskSpaceGuard.decide(Long.MAX_VALUE, 512L, 128L));
    }
}
