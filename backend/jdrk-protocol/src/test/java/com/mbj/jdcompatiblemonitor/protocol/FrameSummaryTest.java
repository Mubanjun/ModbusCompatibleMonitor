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

package com.mbj.jdcompatiblemonitor.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mbj.jdcompatiblemonitor.common.Hex;
import org.junit.jupiter.api.Test;

/** 信令报文摘要的单元测试（FR-SIG-01）。 */
class FrameSummaryTest {

    @Test
    void describesRtuReadRequest() {
        assertEquals("发送 读保持寄存器 起始=0 数量=2",
            FrameSummary.describe(false, true, Hex.fromHex("01 03 00 00 00 02 C4 0B")));
    }

    @Test
    void describesRtuReadResponse() {
        assertEquals("接收 寄存器=[742, 199]",
            FrameSummary.describe(false, false, Hex.fromHex("01 03 04 02 E6 00 C7 1A 4A")));
    }

    @Test
    void describesRtuException() {
        String summary = FrameSummary
            .describe(false, false, Hex.fromHex("01 83 02 C0 F1"));
        assertTrue(summary.contains("异常应答"), summary);
        assertTrue(summary.contains("非法数据地址"), summary);
    }

    @Test
    void describesWriteRequests() {
        assertEquals("发送 写单寄存器 地址=16 值=4660",
            FrameSummary.describe(false, true, Hex.fromHex("01 06 00 10 12 34 8C 0A")));
        assertEquals("发送 写多寄存器 起始=1 数量=2",
            FrameSummary.describe(false, true,
                Hex.fromHex("01 10 00 01 00 02 04 00 01 00 02 00 00")));
    }

    @Test
    void describesTcpFrames() {
        byte[] request = Hex.fromHex("00 01 00 00 00 06 01 03 00 00 00 02");
        assertEquals("发送 读保持寄存器 起始=0 数量=2",
            FrameSummary.describe(true, true, request));
    }

    @Test
    void degradesGracefullyOnShortFrame() {
        assertEquals("发送 2 字节", FrameSummary.describe(false, true, Hex.fromHex("01 03")));
        assertEquals("空报文", FrameSummary.describe(false, true, new byte[0]));
        assertEquals("接收 长度不足（声明 8 字节）",
            FrameSummary.describe(false, false, Hex.fromHex("01 03 08 00 01")));
    }

    @Test
    void truncatesLongHexDumps() {
        byte[] frame = new byte[1200];
        assertEquals(1000 + 4, FrameSummary.toHex(frame).length());
        assertEquals("", FrameSummary.toHex(null));
    }
}
