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

import com.mbj.jdcompatiblemonitor.protocol.transport.DeviceTransport;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modbus TCP（FR-PROTO-03）。
 *
 * <p>在 PDU 外层包 MBAP 报文头：事务标识(2) + 协议标识(2，固定 0) + 长度(2) + 单元标识(1)，
 * 长度字段等于「单元标识 + PDU」的字节数，因此应答长度可先读报文头再确定。</p>
 */
public final class ModbusTcpProtocol extends AbstractModbusProtocol {

    private static final int MBAP_LENGTH = 7;

    private final AtomicInteger transactionId = new AtomicInteger(1);

    public ModbusTcpProtocol(DeviceTransport transport, int timeoutMs, int retries) {
        super(transport, timeoutMs, retries);
    }

    @Override
    public String name() {
        return "Modbus TCP";
    }

    @Override
    protected byte[] exchange(byte[] pdu, int slaveAddress, int functionCode) {
        int tid = transactionId.getAndIncrement() & 0xFFFF;
        int length = 1 + pdu.length;
        byte[] request = new byte[MBAP_LENGTH + pdu.length];
        request[0] = (byte) ((tid >>> 8) & 0xFF);
        request[1] = (byte) (tid & 0xFF);
        request[2] = 0;
        request[3] = 0;
        request[4] = (byte) ((length >>> 8) & 0xFF);
        request[5] = (byte) (length & 0xFF);
        request[6] = (byte) slaveAddress;
        System.arraycopy(pdu, 0, request, MBAP_LENGTH, pdu.length);

        try {
            transport.write(request);
            this.traceOut(slaveAddress, request);

            byte[] header = transport.readExactly(MBAP_LENGTH, this.currentTimeoutMs());
            int responseTid = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
            int responseLength = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
            int responseUnit = header[6] & 0xFF;
            if (responseTid != tid) {
                this.traceError(slaveAddress, "事务标识不匹配，期望 " + tid + "，实际 " + responseTid);
                throw new ProtocolException("事务标识不匹配：期望 " + tid + "，实际 " + responseTid);
            }
            if (responseUnit != slaveAddress) {
                this.traceError(slaveAddress, "单元标识不匹配，收到 " + responseUnit);
                throw new ProtocolException("单元标识不匹配：期望 " + slaveAddress + "，实际 " + responseUnit);
            }
            int pduLength = responseLength - 1;
            if (pduLength <= 0) {
                throw new ProtocolException("应答长度非法：" + responseLength);
            }
            byte[] responsePdu = transport.readExactly(pduLength, this.currentTimeoutMs());
            this.traceIn(slaveAddress, concatenate(header, responsePdu));
            return responsePdu;
        } catch (ProtocolException e) {
            throw e;
        } catch (Exception e) {
            this.traceError(slaveAddress, e.getMessage());
            throw new ProtocolException("TCP 收发失败：" + e.getMessage(), e);
        }
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
