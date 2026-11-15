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

package com.mbj.jdcompatiblemonitor.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 用现场实测帧验证 CRC16。 */
class Crc16ModbusTest {

    @Test
    void computesKnownModbusFrameCrc() {
        // 现场实测的问询帧：01 03 00 00 00 02 C4 0B
        byte[] frame = Hex.fromHex("01 03 00 00 00 02 C4 0B");
        // 线上顺序是低字节在前：C4 0B，因此数值为 0x0BC4
        assertEquals(0x0BC4, Crc16Modbus.compute(frame, 0, 6));
        assertTrue(Crc16Modbus.verify(frame, frame.length));
    }

    @Test
    void verifiesRealResponseFrame() {
        // 现场实测的应答帧：01 03 04 2C 13 9C 89 AB C0
        byte[] frame = Hex.fromHex("01 03 04 2C 13 9C 89 AB C0");
        assertTrue(Crc16Modbus.verify(frame, frame.length));
    }

    @Test
    void appendsCrcInLowHighOrder() {
        byte[] payload = Hex.fromHex("01 03 00 00 00 02");
        byte[] withCrc = Crc16Modbus.withCrc(payload);
        assertArrayEquals(Hex.fromHex("01 03 00 00 00 02 C4 0B"), withCrc);
    }

    @Test
    void rejectsCorruptedFrame() {
        byte[] frame = Hex.fromHex("01 03 04 2C 13 9C 89 AB C1");
        assertTrue(!Crc16Modbus.verify(frame, frame.length));
    }
}
