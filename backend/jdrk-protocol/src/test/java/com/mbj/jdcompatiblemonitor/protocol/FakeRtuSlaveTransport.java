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
import com.mbj.jdcompatiblemonitor.protocol.transport.DeviceTransport;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 内存 Modbus RTU 从站，用于在没有物理串口的情况下验证协议层。
 *
 * <p>把写出的请求解析为一次从站应答塞回接收缓冲，从而完整走通
 * 「组帧 -> 收发 -> 解帧 -> CRC 校验」链路。</p>
 */
final class FakeRtuSlaveTransport implements DeviceTransport {

    private final int slaveAddress;
    private final int[] holding;
    private final int[] input;
    private final ArrayDeque<Integer> incoming = new ArrayDeque<>();
    private final List<byte[]> requests = new ArrayList<>();

    private boolean open;
    private boolean mute;
    private boolean corruptResponseCrc;
    private int forcedExceptionCode = -1;
    private int remainSilentWrites;
    private int wrongByteCountReads;
    private int openCount;

    FakeRtuSlaveTransport(int slaveAddress, int[] holding, int[] input) {
        this.slaveAddress = slaveAddress;
        this.holding = holding.clone();
        this.input = input.clone();
    }

    /** 令从站不回应答，用于模拟超时。 */
    void mute() {
        this.mute = true;
    }

    /** 令前 n 次写入不回应答，用于验证重试。 */
    void silentForFirstWrites(int n) {
        this.remainSilentWrites = n;
    }

    /** 令应答 CRC 出错，用于验证校验拦截。 */
    void corruptResponseCrc() {
        this.corruptResponseCrc = true;
    }

    /** 令前 n 次读应答多回 1 个寄存器（模拟上一帧应答迟到被本次读到）。 */
    void wrongByteCountForFirstReads(int n) {
        this.wrongByteCountReads = n;
    }

    /** 令从站固定返回指定异常码。 */
    void forceException(int code) {
        this.forcedExceptionCode = code;
    }

    /** 收到的原始请求帧。 */
    List<byte[]> requests() {
        return new ArrayList<>(requests);
    }

    /** 通道被打开的次数。 */
    int openCount() {
        return openCount;
    }

    /** 当前保持寄存器值。 */
    int[] holding() {
        return holding.clone();
    }

    @Override
    public void open() {
        open = true;
        openCount++;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void write(byte[] data) throws IOException {
        requests.add(data.clone());
        if (mute || remainSilentWrites > 0) {
            remainSilentWrites--;
            return;
        }
        if (!Crc16Modbus.verify(data, data.length)) {
            throw new IOException("请求 CRC 校验失败");
        }
        byte[] response = this.respond(data);
        if (response == null) {
            return;
        }
        if (corruptResponseCrc && response.length >= 3) {
            response[response.length - 1] = (byte) (response[response.length - 1] ^ 0xFF);
        }
        for (byte b : response) {
            incoming.addLast(b & 0xFF);
        }
    }

    @Override
    public byte[] readExactly(int expectedLength, int timeoutMs) throws IOException {
        byte[] out = new byte[expectedLength];
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 20);
        for (int i = 0; i < expectedLength; i++) {
            while (incoming.isEmpty()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IOException("模拟从站无应答，读取超时");
                }
                DeviceTransport.sleepQuietly(2);
            }
            out[i] = (byte) (int) incoming.pollFirst();
        }
        return out;
    }

    @Override
    public void purge() {
        incoming.clear();
    }

    @Override
    public String description() {
        return "内存从站 " + slaveAddress;
    }

    @Override
    public void close() {
        open = false;
    }

    /** 按请求生成应答帧。 */
    private byte[] respond(byte[] request) {
        if (request.length < 4) {
            return null;
        }
        int slave = request[0] & 0xFF;
        int function = request[1] & 0xFF;
        if (slave != slaveAddress) {
            return null;
        }
        if (forcedExceptionCode > 0) {
            return frame(new byte[] {(byte) slave, (byte) (function | 0x80),
                (byte) forcedExceptionCode});
        }
        if (function == 0x03 || function == 0x04) {
            int start = ((request[2] & 0xFF) << 8) | (request[3] & 0xFF);
            int count = ((request[4] & 0xFF) << 8) | (request[5] & 0xFF);
            int[] table = function == 0x03 ? holding : input;
            if (count < 1 || start < 0 || start + count > table.length) {
                return frame(new byte[] {(byte) slave, (byte) (function | 0x80), 2});
            }
            int actual = count;
            if (wrongByteCountReads > 0) {
                wrongByteCountReads--;
                actual = Math.min(count + 1, table.length - start);
            }
            byte[] pdu = new byte[2 + actual * 2];
            pdu[0] = (byte) function;
            pdu[1] = (byte) (actual * 2);
            for (int i = 0; i < actual; i++) {
                pdu[2 + i * 2] = (byte) ((table[start + i] >>> 8) & 0xFF);
                pdu[3 + i * 2] = (byte) (table[start + i] & 0xFF);
            }
            byte[] payload = new byte[1 + pdu.length];
            payload[0] = (byte) slave;
            System.arraycopy(pdu, 0, payload, 1, pdu.length);
            return frame(payload);
        }
        if (function == 0x06) {
            int register = ((request[2] & 0xFF) << 8) | (request[3] & 0xFF);
            int value = ((request[4] & 0xFF) << 8) | (request[5] & 0xFF);
            if (register < holding.length) {
                holding[register] = value;
            }
            byte[] payload = new byte[7];
            payload[0] = (byte) slave;
            System.arraycopy(request, 1, payload, 1, 6);
            return frame(payload);
        }
        if (function == 0x10) {
            int start = ((request[2] & 0xFF) << 8) | (request[3] & 0xFF);
            int count = ((request[4] & 0xFF) << 8) | (request[5] & 0xFF);
            for (int i = 0; i < count; i++) {
                int value = ((request[7 + i * 2] & 0xFF) << 8) | (request[8 + i * 2] & 0xFF);
                if (start + i < holding.length) {
                    holding[start + i] = value;
                }
            }
            byte[] payload = new byte[6];
            payload[0] = (byte) slave;
            System.arraycopy(request, 1, payload, 1, 5);
            return frame(payload);
        }
        return frame(new byte[] {(byte) slave, (byte) (function | 0x80), 1});
    }

    /** 尾部补 CRC。 */
    private static byte[] frame(byte[] payload) {
        return Crc16Modbus.withCrc(payload);
    }
}
