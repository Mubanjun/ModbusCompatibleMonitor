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

import java.nio.charset.StandardCharsets;

/** Modbus ASCII 协议实现的单元测试（内存从站，无需硬件）。 */
class ModbusAsciiProtocolTest {

    private static final int[] HOLDING = {742, 199, 11457, 29061, 0, 9, 0, 0};

    @Test
    void lrcShouldMatchModbusAsciiExample() {
        byte[] payload = {0x01, 0x03, 0x00, 0x00, 0x00, 0x02};
        assertEquals(0xFA, ModbusAsciiProtocol.lrc(payload));
        assertEquals(":010300000002FA" + (char) 13 + (char) 10, new String(ModbusAsciiProtocol
            .encode(payload), StandardCharsets.US_ASCII));
    }

    @Test
    void decodeShouldValidateFramingAndLrc() {
        byte[] good = ModbusAsciiProtocol.encode(new byte[] {0x01, 0x03, 0x02, 0x02, (byte) 0xE6});
        assertArrayEquals(new byte[] {0x01, 0x03, 0x02, 0x02, (byte) 0xE6}, ModbusAsciiProtocol
            .decode(good));

        byte[] badStart = good.clone();
        badStart[0] = '!';
        assertThrows(ProtocolException.class, () -> ModbusAsciiProtocol.decode(badStart));

        byte[] badEnding = good.clone();
        badEnding[badEnding.length - 1] = 'X';
        assertThrows(ProtocolException.class, () -> ModbusAsciiProtocol.decode(badEnding));

        byte[] badLrc = good.clone();
        badLrc[badLrc.length - 4] = '0';
        badLrc[badLrc.length - 3] = '0';
        assertThrows(ProtocolException.class, () -> ModbusAsciiProtocol.decode(badLrc));

        assertThrows(ProtocolException.class, () -> ModbusAsciiProtocol.decode(new byte[] {':'}));
    }

    @Test
    void readsHoldingRegistersOverAscii() {
        FakeAsciiSlaveTransport slave = new FakeAsciiSlaveTransport(1, HOLDING);
        try (ModbusAsciiProtocol protocol = new ModbusAsciiProtocol(slave, 500, 0)) {
            protocol.connect();
            int[] values = protocol.readHoldingRegisters(1, 0, 2);
            assertArrayEquals(new int[] {742, 199}, values);
            assertEquals(":010300000002FA" + (char) 13 + (char) 10, new String(slave.requests()
                .get(0), StandardCharsets.US_ASCII));
        }
    }

    @Test
    void writesSingleRegisterOverAscii() {
        FakeAsciiSlaveTransport slave = new FakeAsciiSlaveTransport(1, HOLDING);
        try (ModbusAsciiProtocol protocol = new ModbusAsciiProtocol(slave, 500, 0)) {
            protocol.writeSingleRegister(1, 2, 1);
            assertEquals(1, slave.holding()[2]);
        }
    }

    @Test
    void writesMultipleRegistersOverAscii() {
        FakeAsciiSlaveTransport slave = new FakeAsciiSlaveTransport(1, HOLDING);
        try (ModbusAsciiProtocol protocol = new ModbusAsciiProtocol(slave, 500, 0)) {
            protocol.writeMultipleRegisters(1, 0, new int[] {1, 0, 1, 0, 1, 0, 1, 0});
            assertArrayEquals(new int[] {1, 0, 1, 0, 1, 0, 1, 0}, slave.holding());
        }
    }

    @Test
    void shouldSurfaceExceptionResponse() {
        FakeAsciiSlaveTransport slave = new FakeAsciiSlaveTransport(1, HOLDING);
        slave.forceException(2);
        try (ModbusAsciiProtocol protocol = new ModbusAsciiProtocol(slave, 500, 0)) {
            ProtocolException error = assertThrows(ProtocolException.class, () -> protocol
                .readHoldingRegisters(1, 0, 2));
            assertTrue(error.getMessage().contains("2"), error.getMessage());
            assertTrue(error.getMessage().contains("非法数据地址"), error.getMessage());
        }
    }

    @Test
    void shouldRejectCorruptedResponseLrc() {
        FakeAsciiSlaveTransport slave = new FakeAsciiSlaveTransport(1, HOLDING);
        slave.corruptLrc();
        try (ModbusAsciiProtocol protocol = new ModbusAsciiProtocol(slave, 500, 0)) {
            ProtocolException error = assertThrows(ProtocolException.class, () -> protocol
                .readHoldingRegisters(1, 0, 2));
            assertTrue(error.getMessage().contains("LRC"), error.getMessage());
        }
    }

    @Test
    void factoryShouldCreateAsciiProtocol() {
        DeviceProtocol protocol =
            Protocols.create(Protocols.MODBUS_ASCII, Protocols.PORT_TYPE_SERIAL,
                "COM_TEST", 9600, 8, 1, 0, 0, 500, 0);
        assertEquals("Modbus ASCII", protocol.name());
        protocol.close();
        assertEquals(0, ModbusAsciiProtocol.lrc(new byte[] {0}));
        assertEquals(0xFC, ModbusAsciiProtocol.lrc(Hex.fromHex("01 03")));
    }
}
