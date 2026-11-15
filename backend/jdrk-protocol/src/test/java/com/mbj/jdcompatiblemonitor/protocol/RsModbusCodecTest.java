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

import com.mbj.jdcompatiblemonitor.common.Hex;
import org.junit.jupiter.api.Test;

/** 用抓包与现场实测帧验证 RS-Modbus 编解码。 */
class RsModbusCodecTest {

    @Test
    void buildsReadRequestMatchingFieldCapture() {
        // 现场抓到的问询帧
        byte[] request =
            RsModbusCodec.buildReadRequest(1, RsModbusCodec.FUNCTION_READ_HOLDING, 0, 2);
        assertEquals("01 03 00 00 00 02 C4 0B", Hex.toHex(request, " "));
    }

    @Test
    void parsesRealResponseForPhChannel() {
        // 现场实测：ch2 应答 01 03 04 02 2C 13 9C 89 ...（此处用最短实测帧）
        byte[] frame = Hex.fromHex("01 03 04 2C 13 9C 89 AB C0");
        int[] registers =
            RsModbusCodec.parseReadResponse(frame, 1, RsModbusCodec.FUNCTION_READ_HOLDING);
        assertArrayEquals(new int[] {0x2C13, 0x9C89}, registers);
    }

    @Test
    void parsesPhAndTemperatureRegisters() {
        // ch2：0x0000 = PH 原始值 742，0x0001 = 温度原始值 199
        byte[] frame = Hex.fromHex("01 03 04 02 E6 00 C7");
        byte[] full = com.mbj.jdcompatiblemonitor.common.Crc16Modbus.withCrc(frame);
        int[] registers =
            RsModbusCodec.parseReadResponse(full, 1, RsModbusCodec.FUNCTION_READ_HOLDING);
        assertEquals(742, registers[0]);
        assertEquals(199, registers[1]);
    }

    @Test
    void decodesFloatChannel() {
        // ch3 溶解氧：0x4105 / 0x0C72 -> 8.3155 mg/L
        int[] registers = new int[] {0x4105, 0x0C72};
        double value = ValueEncoding.FLOAT32_BIG_ENDIAN.decode(registers, 0);
        assertEquals(8.3155D, value, 0.0001D);
    }

    @Test
    void decodesPhAndTemperatureWithHostCoefficient() {
        assertEquals(7.42D, ValueEncoding.UINT16_SCALE_100.decode(new int[] {742}, 0), 1e-9);
        assertEquals(19.9D, ValueEncoding.INT16_SCALE_10.decode(new int[] {199}, 0), 1e-9);
        assertEquals(11457D, ValueEncoding.UINT16_SCALE_1.decode(new int[] {11457}, 0), 1e-9);
    }

    @Test
    void decodesNegativeTemperature() {
        // 温度低于 0 摄氏度时以补码上传：FF9B -> -10.1 ℃
        assertEquals(-10.1D, ValueEncoding.INT16_SCALE_10.decode(new int[] {0xFF9B}, 0), 1e-9);
    }
}
