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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mbj.jdcompatiblemonitor.common.Hex;
import org.junit.jupiter.api.Test;

/** Modbus RTU / 仁科 RS-Modbus 协议实现的单元测试（内存从站，无需硬件）。 */
class ModbusRtuProtocolTest {

    /** 一个典型的 8 通道采集盒：保持寄存器 0..7 为通道值。 */
    private static FakeRtuSlaveTransport slave() {
        return new FakeRtuSlaveTransport(1, new int[] {11, 22, 33, 44, 55, 66, 77, 88},
            new int[] {101, 202, 303});
    }

    @Test
    void readsHoldingRegisters() {
        FakeRtuSlaveTransport slave = slave();
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 200, 0)) {
            protocol.connect();
            int[] values = protocol.readHoldingRegisters(1, 0, 8);
            assertArrayEquals(new int[] {11, 22, 33, 44, 55, 66, 77, 88}, values);
            assertEquals("01 03 00 00 00 08", Hex.toHex(slave.requests().get(0), " ")
                .substring(0, 17));
        }
    }

    @Test
    void readsInputRegisters() {
        FakeRtuSlaveTransport slave = slave();
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 200, 0)) {
            assertArrayEquals(new int[] {202, 303}, protocol.readInputRegisters(1, 1, 2));
        }
    }

    @Test
    void readsRsModbusElementPair() {
        // 仁科 RS-Modbus：0x0000 为模拟量2、0x0001 为模拟量1，一次读 2 个寄存器即可
        FakeRtuSlaveTransport slave = new FakeRtuSlaveTransport(3,
            new int[] {0x1234, 0x5678}, new int[0]);
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 200, 0)) {
            assertArrayEquals(new int[] {0x1234, 0x5678}, protocol.readHoldingRegisters(3, 0, 2));
        }
    }

    @Test
    void writesSingleAndMultipleRegisters() {
        FakeRtuSlaveTransport slave = slave();
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 200, 0)) {
            protocol.writeSingleRegister(1, 2, 999);
            assertEquals(999, slave.holding()[2]);
            protocol.writeMultipleRegisters(1, 0, new int[] {1, 2});
            assertArrayEquals(new int[] {1, 2}, new int[] {slave.holding()[0], slave.holding()[1]});
        }
    }

    @Test
    void reportsSlaveException() {
        FakeRtuSlaveTransport slave = slave();
        slave.forceException(2);
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 200, 0)) {
            ProtocolException e = assertThrows(ProtocolException.class,
                () -> protocol.readHoldingRegisters(1, 0, 2));
            assertTrue(e.getMessage().contains("非法数据地址"), e.getMessage());
        }
    }

    @Test
    void rejectsWrongSlaveAddress() {
        FakeRtuSlaveTransport slave = slave();
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 200, 0)) {
            // 从站配置为 1，却按 5 号地址访问：无应答，最终超时
            ProtocolException e = assertThrows(ProtocolException.class,
                () -> protocol.readHoldingRegisters(5, 0, 2));
            assertTrue(e.getMessage().contains("重试") || e.getMessage().contains("超时"),
                e.getMessage());
        }
    }

    @Test
    void rejectsCorruptedResponseCrc() {
        FakeRtuSlaveTransport slave = slave();
        slave.corruptResponseCrc();
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 200, 0)) {
            ProtocolException e = assertThrows(ProtocolException.class,
                () -> protocol.readHoldingRegisters(1, 0, 2));
            assertTrue(e.getMessage().contains("CRC"), e.getMessage());
        }
    }

    @Test
    void retriesAfterTransientTimeout() {
        FakeRtuSlaveTransport slave = slave();
        slave.silentForFirstWrites(1);
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 50, 2)) {
            assertArrayEquals(new int[] {11, 22}, protocol.readHoldingRegisters(1, 0, 2));
            assertTrue(slave.openCount() >= 2, "重试前应重开通道");
        }
    }

    @Test
    void retriesWhenResponseRegisterCountMismatches() {
        // 模拟 485 总线上一帧应答迟到：首次读回多 1 个寄存器，必须拒绝并重试，
        // 否则 11/22 会被错位当成别的通道值写进实时值
        FakeRtuSlaveTransport slave = slave();
        slave.wrongByteCountForFirstReads(1);
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 50, 2)) {
            assertArrayEquals(new int[] {11, 22}, protocol.readHoldingRegisters(1, 0, 2));
            assertTrue(slave.openCount() >= 2, "字节数不符应重开通道重试");
        }
    }

    @Test
    void reportsRegisterCountMismatchAfterRetries() {
        FakeRtuSlaveTransport slave = slave();
        slave.wrongByteCountForFirstReads(9);
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 50, 1)) {
            ProtocolException e = assertThrows(ProtocolException.class,
                () -> protocol.readHoldingRegisters(1, 0, 2));
            assertTrue(e.getMessage().contains("应答寄存器数量与请求不符"), e.getMessage());
        }
    }

    @Test
    void failsAfterExhaustingRetries() {
        FakeRtuSlaveTransport slave = slave();
        slave.mute();
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 30, 1)) {
            ProtocolException e = assertThrows(ProtocolException.class,
                () -> protocol.readHoldingRegisters(1, 0, 2));
            assertTrue(e.getMessage().contains("重试 1 次后仍失败"), e.getMessage());
            assertEquals(2, slave.requests().size(), "首次 + 1 次重试");
        }
    }

    @Test
    void honoursPerCallTimeoutOverride() {
        FakeRtuSlaveTransport slave = slave();
        slave.mute();
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 30, 0)) {
            long start = System.currentTimeMillis();
            assertThrows(ProtocolException.class, () -> protocol.callWithTimeout(200,
                () -> protocol.readHoldingRegisters(1, 0, 2)));
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed >= 180, "应等待覆盖后的 200ms，实际 " + elapsed);
        }
    }

    @Test
    void collectsAndDrainsFrameTraces() {
        FakeRtuSlaveTransport slave = slave();
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 200, 0)) {
            protocol.readHoldingRegisters(1, 0, 2);
            var traces = protocol.drainTraces();
            assertEquals(2, traces.size(), "一收一发");
            assertTrue(traces.get(0).outbound());
            assertFalse(traces.get(1).outbound());
            assertEquals(1, traces.get(0).slaveAddress());
            assertTrue(protocol.drainTraces().isEmpty(), "取走后应清空");
        }
    }

    @Test
    void checksHealthWithMinimalRead() {
        FakeRtuSlaveTransport slave = slave();
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 200, 0)) {
            protocol.connect();
            assertTrue(protocol.isConnected());
            protocol.checkHealth();
            protocol.close();
            assertFalse(protocol.isConnected());
        }
    }

    @Test
    void transactsRawFrame() {
        FakeRtuSlaveTransport slave = slave();
        try (ModbusRtuProtocol protocol = new ModbusRtuProtocol(slave, 200, 0)) {
            byte[] frame = com.mbj.jdcompatiblemonitor.common.Crc16Modbus
                .withCrc(Hex.fromHex("01 03 00 00 00 02"));
            byte[] response = protocol.transact(frame, 9);
            assertEquals(9, response.length);
            assertTrue(com.mbj.jdcompatiblemonitor.common.Crc16Modbus.verify(response,
                response.length));
        }
    }
}
