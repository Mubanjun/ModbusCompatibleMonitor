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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mbj.jdcompatiblemonitor.protocol.transport.TcpTransport;
import org.junit.jupiter.api.Test;

/** Modbus TCP 协议实现的端到端测试（进程内从站，无需硬件）。 */
class ModbusTcpProtocolTest {

    @Test
    void readsHoldingRegistersOverTcp() throws Exception {
        try (FakeModbusTcpSlave slave = new FakeModbusTcpSlave(new int[] {7, 8, 9}, new int[0])) {
            try (ModbusTcpProtocol protocol = new ModbusTcpProtocol(
                TcpTransport.parse(slave.address(), 500), 500, 0)) {
                protocol.connect();
                assertArrayEquals(new int[] {7, 8, 9}, protocol.readHoldingRegisters(1, 0, 3));
            }
        }
    }

    @Test
    void readsInputRegistersOverTcp() throws Exception {
        try (FakeModbusTcpSlave slave = new FakeModbusTcpSlave(new int[0], new int[] {5, 6})) {
            try (ModbusTcpProtocol protocol = new ModbusTcpProtocol(
                TcpTransport.parse(slave.address(), 500), 500, 0)) {
                assertArrayEquals(new int[] {6}, protocol.readInputRegisters(1, 1, 1));
            }
        }
    }

    @Test
    void incrementsTransactionIdPerRequest() throws Exception {
        try (FakeModbusTcpSlave slave = new FakeModbusTcpSlave(new int[] {1, 2}, new int[0])) {
            try (ModbusTcpProtocol protocol = new ModbusTcpProtocol(
                TcpTransport.parse(slave.address(), 500), 500, 0)) {
                protocol.readHoldingRegisters(1, 0, 1);
                protocol.readHoldingRegisters(1, 1, 1);
            }
            assertEquals(2, slave.transactionIds().size());
            assertEquals(1, slave.transactionIds().get(0));
            assertEquals(2, slave.transactionIds().get(1));
        }
    }

    @Test
    void reconnectsAndRetriesAfterDrop() throws Exception {
        try (FakeModbusTcpSlave slave = new FakeModbusTcpSlave(new int[] {42}, new int[0])) {
            slave.dropFirstConnection();
            try (ModbusTcpProtocol protocol = new ModbusTcpProtocol(
                TcpTransport.parse(slave.address(), 500), 500, 1)) {
                assertArrayEquals(new int[] {42}, protocol.readHoldingRegisters(1, 0, 1));
                assertTrue(slave.connectionCount() >= 2, "应建立第二次连接");
            }
        }
    }

    @Test
    void propagatesSlaveException() throws Exception {
        try (FakeModbusTcpSlave slave = new FakeModbusTcpSlave(new int[] {1}, new int[0])) {
            slave.forceException(3);
            try (ModbusTcpProtocol protocol = new ModbusTcpProtocol(
                TcpTransport.parse(slave.address(), 500), 500, 0)) {
                ProtocolException e = assertThrows(ProtocolException.class,
                    () -> protocol.readHoldingRegisters(1, 0, 1));
                assertTrue(e.getMessage().contains("非法数据值"), e.getMessage());
            }
        }
    }

    @Test
    void rejectsMalformedAddress() {
        assertThrows(IllegalArgumentException.class, () -> TcpTransport.parse("192.168.1.10", 100));
        assertThrows(IllegalArgumentException.class, () -> TcpTransport.parse(":502", 100));
    }

    @Test
    void factoryPicksProtocolByConfig() {
        DeviceProtocol tcp = Protocols.create(Protocols.MODBUS_TCP, Protocols.PORT_TYPE_TCP,
            "127.0.0.1:502", 9600, 8, 1, 0, 0, 500, 0);
        assertInstanceOf(ModbusTcpProtocol.class, tcp);
        assertEquals("Modbus TCP", tcp.name());

        DeviceProtocol rtu = Protocols.create(Protocols.RS_MODBUS, Protocols.PORT_TYPE_SERIAL,
            "COM9", 9600, 8, 1, 0, 0, 500, 0);
        assertInstanceOf(ModbusRtuProtocol.class, rtu);
        assertEquals("Modbus RTU", rtu.name());
    }
}
