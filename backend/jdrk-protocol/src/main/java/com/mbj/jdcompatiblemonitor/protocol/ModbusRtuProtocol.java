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

import com.mbj.jdcompatiblemonitor.common.Crc16Modbus;
import com.mbj.jdcompatiblemonitor.common.Hex;
import com.mbj.jdcompatiblemonitor.protocol.transport.DeviceTransport;

/**
 * Modbus RTU（FR-PROTO-01）与仁科 RS-Modbus（FR-PROTO-02）。
 *
 * <p>两者帧格式一致（从站地址 + PDU + CRC16，低字节在前），
 * 差异只在寄存器语义——RS-Modbus 规定 0x0000 为模拟量2、0x0001 为模拟量1，
 * 该语义由通道配置层负责，协议层无需区分。</p>
 *
 * <p>应答长度事先不可知，因此先读 3 字节（从站、功能码、字节数），
 * 再按字节数续读数据与 CRC。</p>
 */
public final class ModbusRtuProtocol extends AbstractModbusProtocol {

    /** 写应答（功能码 06/16）在首 3 字节之后固定还有 5 字节：地址低位 + 值/数量 + CRC */
    private static final int WRITE_RESPONSE_TAIL = 5;

    public ModbusRtuProtocol(DeviceTransport transport, int timeoutMs, int retries) {
        super(transport, timeoutMs, retries);
    }

    @Override
    public String name() {
        return "Modbus RTU";
    }

    @Override
    protected byte[] exchange(byte[] pdu, int slaveAddress, int functionCode) {
        byte[] payload = new byte[1 + pdu.length];
        payload[0] = (byte) slaveAddress;
        System.arraycopy(pdu, 0, payload, 1, pdu.length);
        byte[] request = Crc16Modbus.withCrc(payload);

        try {
            transport.purge();
            transport.write(request);
            this.traceOut(slaveAddress, request);

            byte[] head = transport.readExactly(3, this.currentTimeoutMs());
            int slave = head[0] & 0xFF;
            int function = head[1] & 0xFF;
            if (slave != slaveAddress) {
                this.traceError(slaveAddress, "从站地址不匹配，收到 " + slave);
                throw new ProtocolException("从站地址不匹配：期望 " + slaveAddress + "，实际 " + slave);
            }
            if ((function & 0x80) != 0) {
                // 异常应答固定为 5 字节：从站 + 功能码 + 异常码 + CRC16，异常码位于第 3 字节
                byte[] rest = transport.readExactly(2, this.currentTimeoutMs());
                byte[] frame = concatenate(head, rest);
                this.traceIn(slaveAddress, frame);
                if (!Crc16Modbus.verify(frame, frame.length)) {
                    throw new ProtocolException("异常应答 CRC 校验失败：" + Hex.toHex(frame, " "));
                }
                return new byte[] {(byte) function, head[2]};
            }

            // 读应答第 3 字节是字节数，写应答（06/16）没有字节数而是固定 5 字节回显余量
            int remaining = isReadFunction(function) ? (head[2] & 0xFF) + 2 : WRITE_RESPONSE_TAIL;
            byte[] rest = transport.readExactly(remaining, this.currentTimeoutMs());
            byte[] frame = concatenate(head, rest);
            this.traceIn(slaveAddress, frame);
            if (!Crc16Modbus.verify(frame, frame.length)) {
                this.traceError(slaveAddress, "CRC 校验失败");
                throw new ProtocolException("CRC 校验失败：" + Hex.toHex(frame, " "));
            }
            // 去掉从站地址与尾部 CRC 即为 PDU
            byte[] responsePdu = new byte[frame.length - 3];
            System.arraycopy(frame, 1, responsePdu, 0, responsePdu.length);
            return responsePdu;
        } catch (ProtocolException e) {
            throw e;
        } catch (Exception e) {
            this.traceError(slaveAddress, e.getMessage());
            throw new ProtocolException("RTU 收发失败：" + e.getMessage(), e);
        }
    }

    /**
     * 是否为读寄存器应答（第 3 字节为字节数）
     *
     * @param functionCode 功能码
     * @return 是否读功能码
     */
    private static boolean isReadFunction(int functionCode) {
        return functionCode == ModbusPdu.FUNCTION_READ_HOLDING
            || functionCode == ModbusPdu.FUNCTION_READ_INPUT;
    }

    /**
     * 拼接字节数组
     *
     * @param first  前段
     * @param second 后段
     * @return 拼接结果
     */
    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
