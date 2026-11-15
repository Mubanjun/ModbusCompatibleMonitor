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

package com.mbj.jdcompatiblemonitor.protocol.rk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mbj.jdcompatiblemonitor.common.Hex;
import org.junit.jupiter.api.Test;

/** 用现场 2404 抓包验证仁科帧编解码。 */
class RkFrameCodecTest {

    /** 现场抓到的实时数据上报帧（134 字节）。 */
    private static final String REALTIME_FRAME =
        "68 88 88 88 88 88 88 68 A0 7A 36 1D 34 33 33 33 33 35 33 33 33 33 "
            + "36 33 33 33 33 37 33 33 33 33 38 33 33 33 33 39 33 33 33 33 3A 33 33 33 33 3B 33 33 33 33 3C 33 33 33 33 "
            + "3D 33 33 33 33 3E 33 33 33 33 3F 33 33 33 33 40 33 33 33 33 41 33 33 33 33 42 33 33 33 33 43 33 33 33 33 "
            + "44 33 33 33 33 45 33 33 33 33 46 33 33 33 33 47 33 33 33 33 3C 46 33 33 2F 7A 33 33 8F B0 EF 35 35 33 33 33 "
            + "4A 3B 75 73 C0 16";

    /** 现场抓到的平台应答帧（18 字节）。 */
    private static final String ACK_FRAME = "68 88 88 88 88 88 88 68 20 06 36 1D 4A 3B 75 73 E6 16";

    @Test
    void parsesRealtimeFrame() {
        RkFrame frame = RkFrameCodec.parse(Hex.fromHex(REALTIME_FRAME));
        assertEquals(134, frame.totalLength());
        assertEquals(122, frame.lengthField());
        assertTrue(frame.checksumValid(), "checksum should be valid");
        assertTrue(frame.isDeviceReport());
        assertEquals("EA03", frame.commandCode());
        assertEquals("4A 3B 75 73", Hex.toHex(frame.deviceAddress(), " "));
    }

    @Test
    void parsesPlatformAckFrame() {
        RkFrame frame = RkFrameCodec.parse(Hex.fromHex(ACK_FRAME));
        assertEquals(18, frame.totalLength());
        assertEquals(6, frame.lengthField());
        assertTrue(frame.checksumValid());
        assertTrue(frame.isPlatformAck());
        assertEquals("EA03", frame.commandCode());
    }

    @Test
    void checksumIsAdditiveLowByte() {
        byte[] raw = Hex.fromHex(ACK_FRAME);
        assertEquals(0xE6, RkFrameCodec.checksum(raw, 0, 16));
    }

    @Test
    void reportsFrameLengthBeforeFullArrival() {
        byte[] raw = Hex.fromHex(REALTIME_FRAME);
        assertEquals(134, RkFrameCodec.frameLength(raw, 0));
        assertEquals(-1, RkFrameCodec.frameLength(java.util.Arrays.copyOf(raw, 5), 0));
        assertEquals(-2,
            RkFrameCodec.frameLength(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 0));
    }

    @Test
    void offsetRoundTrip() {
        byte[] original = Hex.fromHex("00 01 02 03 7F 80 FF");
        byte[] encoded = RkFrameCodec.encodeOffset(original);
        assertTrue(java.util.Arrays.equals(original, RkFrameCodec.decodeOffset(encoded)));
    }

    @Test
    void buildsFrameThatParsesBack() {
        // 线上负载本身已按 +0x33 偏移，这里直接给出偏移后的形态
        byte[] payload = Hex.fromHex("36 1D 4A 3B 75 73");
        byte[] built = RkFrameCodec.build(Hex.fromHex("88 88 88 88 88 88"), 0x20, payload);
        assertEquals(ACK_FRAME, Hex.toHex(built, " "));
        RkFrame parsed = RkFrameCodec.parse(built);
        assertTrue(parsed.checksumValid());
        assertEquals("EA03", parsed.commandCode());
        assertEquals("4A 3B 75 73", Hex.toHex(parsed.deviceAddress(), " "));
    }
}
