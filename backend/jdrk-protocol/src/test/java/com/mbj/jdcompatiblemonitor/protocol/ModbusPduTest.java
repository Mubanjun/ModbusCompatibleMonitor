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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mbj.jdcompatiblemonitor.common.Hex;
import org.junit.jupiter.api.Test;

/** Modbus PDU 组装与解析的单元测试。 */
class ModbusPduTest {

    @Test
    void buildsReadRequest() {
        byte[] pdu = ModbusPdu.readRequest(ModbusPdu.FUNCTION_READ_HOLDING, 0x0006, 2);
        assertEquals("03 00 06 00 02", Hex.toHex(pdu, " "));
    }

    @Test
    void rejectsIllegalRegisterCount() {
        assertThrows(IllegalArgumentException.class,
            () -> ModbusPdu.readRequest(ModbusPdu.FUNCTION_READ_HOLDING, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> ModbusPdu.readRequest(ModbusPdu.FUNCTION_READ_HOLDING, 0, 126));
    }

    @Test
    void buildsWriteSingleRequest() {
        byte[] pdu = ModbusPdu.writeSingleRequest(0x0010, 0x1234);
        assertEquals("06 00 10 12 34", Hex.toHex(pdu, " "));
    }

    @Test
    void buildsWriteMultipleRequest() {
        byte[] pdu = ModbusPdu.writeMultipleRequest(0x0001, new int[] {0x0102, 0x0304});
        assertEquals("10 00 01 00 02 04 01 02 03 04", Hex.toHex(pdu, " "));
    }

    @Test
    void rejectsEmptyWriteMultipleRequest() {
        assertThrows(IllegalArgumentException.class,
            () -> ModbusPdu.writeMultipleRequest(0, new int[0]));
        assertThrows(IllegalArgumentException.class,
            () -> ModbusPdu.writeMultipleRequest(0, null));
    }

    @Test
    void parsesReadResponse() {
        String hex = "03 04 00 7B 01 2C 10 00 02 05";
        // 功能码 03、字节数 04、两个寄存器 0x007B / 0x012C
        byte[] pdu = Hex.fromHex("03 04 00 7B 01 2C");
        int[] registers = ModbusPdu.parseReadResponse(pdu, ModbusPdu.FUNCTION_READ_HOLDING);
        assertArrayEquals(new int[] {123, 300}, registers);
        assertTrue(hex.contains("00 7B"));
    }

    @Test
    void rejectsShortReadResponse() {
        byte[] pdu = Hex.fromHex("03 06 00 01 00 02");
        ProtocolException e = assertThrows(ProtocolException.class,
            () -> ModbusPdu.parseReadResponse(pdu, ModbusPdu.FUNCTION_READ_HOLDING));
        assertTrue(e.getMessage().contains("应答长度不足"), e.getMessage());
    }

    @Test
    void rejectsRegisterCountMismatch() {
        // 请求 1 个寄存器却收到 2 个（上一帧应答迟到被本次读到）：功能码与 CRC 都正常，
        // 只有字节数能暴露错位，必须拒绝而不是把错位值当通道值用
        byte[] pdu = Hex.fromHex("03 04 02 E6 00 C7");
        assertArrayEquals(new int[] {742, 199},
            ModbusPdu.parseReadResponse(pdu, ModbusPdu.FUNCTION_READ_HOLDING, 2));
        ProtocolException e = assertThrows(ProtocolException.class,
            () -> ModbusPdu.parseReadResponse(pdu, ModbusPdu.FUNCTION_READ_HOLDING, 1));
        assertTrue(e.getMessage().contains("应答寄存器数量与请求不符"), e.getMessage());
    }

    @Test
    void detectsExceptionResponse() {
        byte[] pdu = Hex.fromHex("83 02");
        ProtocolException e = assertThrows(ProtocolException.class,
            () -> ModbusPdu.checkException(pdu, ModbusPdu.FUNCTION_READ_HOLDING));
        assertTrue(e.getMessage().contains("非法数据地址"), e.getMessage());
    }

    @Test
    void detectsFunctionMismatch() {
        byte[] pdu = Hex.fromHex("04 02 00 01");
        ProtocolException e = assertThrows(ProtocolException.class,
            () -> ModbusPdu.checkException(pdu, ModbusPdu.FUNCTION_READ_HOLDING));
        assertTrue(e.getMessage().contains("功能码不匹配"), e.getMessage());
    }

    @Test
    void rejectsTooShortPdu() {
        assertThrows(ProtocolException.class,
            () -> ModbusPdu.checkException(new byte[] {0x03}, ModbusPdu.FUNCTION_READ_HOLDING));
        assertThrows(ProtocolException.class,
            () -> ModbusPdu.checkException(null, ModbusPdu.FUNCTION_READ_HOLDING));
    }

    @Test
    void describesKnownExceptionCodes() {
        assertEquals("非法功能码", ModbusPdu.describeException(1));
        assertEquals("从站设备故障", ModbusPdu.describeException(4));
        assertEquals("从站设备忙", ModbusPdu.describeException(6));
        assertEquals("未知异常码", ModbusPdu.describeException(99));
    }
}
