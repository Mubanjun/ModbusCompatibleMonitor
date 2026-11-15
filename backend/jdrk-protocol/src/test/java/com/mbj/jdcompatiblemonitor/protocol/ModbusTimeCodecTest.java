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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mbj.jdcompatiblemonitor.common.Hex;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

/** 校时寄存器编解码，按需求规格说明书附录 A 的标准 ModBus 规约逐项核对。 */
class ModbusTimeCodecTest {

    @Test
    void encodesAppendixASampleTime() {
        // 附录 A 示例：校时为 2017-03-28 09:59:32，写 684 至 689 共 6 个寄存器
        LocalDateTime time = LocalDateTime.of(2017, 3, 28, 9, 59, 32);
        int[] registers = ModbusTimeCodec.encode(time);
        assertArrayEquals(new int[] {2017, 3, 28, 9, 59, 32}, registers);
        // 示例帧的数据段：05 10 02 AC 00 06 0C 07 E1 00 03 00 1C 00 09 00 3B 00 20
        assertEquals("07 E1 00 03 00 1C 00 09 00 3B 00 20",
            Hex.toHex(encodeRegisters(registers), " "));
    }

    @Test
    void registerBaseAndCountMatchAppendixA() {
        assertEquals(684, ModbusTimeCodec.REGISTER_BASE);
        assertEquals(6, ModbusTimeCodec.REGISTER_COUNT);
    }

    @Test
    void decodesRegistersBackToTime() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 1, 3, 0, 0);
        assertEquals(time, ModbusTimeCodec.decode(ModbusTimeCodec.encode(time)));
    }

    @Test
    void decodesBoundaryYears() {
        assertEquals(LocalDateTime.of(2000, 1, 1, 0, 0, 0), ModbusTimeCodec.decode(new int[] {
            2000, 1, 1, 0, 0, 0
        }));
        assertEquals(LocalDateTime.of(3000, 12, 31, 23, 59, 59), ModbusTimeCodec.decode(new int[] {
            3000, 12, 31, 23, 59, 59
        }));
    }

    @Test
    void ignoresRegistersBeyondTimeRange() {
        // 一次读多个寄存器时，仅前 6 个参与时间解析
        assertEquals(LocalDateTime.of(2026, 10, 1, 8, 30, 15), ModbusTimeCodec.decode(new int[] {
            2026, 10, 1, 8, 30, 15, 9999, 7, 7
        }));
    }

    @Test
    void rejectsUnsupportedYearWhenEncoding() {
        assertThrows(IllegalArgumentException.class, () -> ModbusTimeCodec.encode(LocalDateTime.of(
            1999, 12, 31, 23, 59, 59)));
        assertThrows(IllegalArgumentException.class, () -> ModbusTimeCodec.encode(LocalDateTime.of(
            3001, 1, 1, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> ModbusTimeCodec.encode(null));
    }

    @Test
    void rejectsMalformedRegistersWhenDecoding() {
        // 寄存器个数不足
        assertThrows(IllegalArgumentException.class, () -> ModbusTimeCodec.decode(new int[] {
            2026, 10, 1, 8, 30
        }));
        assertThrows(IllegalArgumentException.class, () -> ModbusTimeCodec.decode(null));
        // 年越界
        assertThrows(IllegalArgumentException.class, () -> ModbusTimeCodec.decode(new int[] {
            1999, 10, 1, 8, 30, 15
        }));
        // 单个寄存器超出 16 位无符号范围
        assertThrows(IllegalArgumentException.class, () -> ModbusTimeCodec.decode(new int[] {
            2026, 10, 1, 8, 30, 65536
        }));
        // 非法月/日
        assertThrows(IllegalArgumentException.class, () -> ModbusTimeCodec.decode(new int[] {
            2026, 13, 1, 8, 30, 15
        }));
        assertThrows(IllegalArgumentException.class, () -> ModbusTimeCodec.decode(new int[] {
            2026, 2, 30, 8, 30, 15
        }));
    }

    /** 按寄存器大端序展开为数据段字节，用于与附录 A 示例帧比对。 */
    private static byte[] encodeRegisters(int[] registers) {
        byte[] bytes = new byte[registers.length * 2];
        for (int index = 0; index < registers.length; index++) {
            bytes[index * 2] = (byte) ((registers[index] >>> 8) & 0xFF);
            bytes[index * 2 + 1] = (byte) (registers[index] & 0xFF);
        }
        return bytes;
    }
}
