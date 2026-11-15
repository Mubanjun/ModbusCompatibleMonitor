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

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 内存 Modbus ASCII 从站（FR-PROTO-04），用于在没有硬件的情况下验证 ASCII 组帧/解帧/LRC。
 *
 * <p>收到请求后按功能码生成 ASCII 应答塞回接收缓冲，完整走通
 * 「组帧 → 收发 → 解帧 → LRC 校验」链路。</p>
 */
final class FakeAsciiSlaveTransport implements DeviceTransport {

    private final int slaveAddress;
    private final int[] holding;
    private final ArrayDeque<Integer> incoming = new ArrayDeque<>();
    private final List<byte[]> requests = new ArrayList<>();

    private boolean open;
    private boolean corruptLrc;
    private int forcedExceptionCode = -1;

    FakeAsciiSlaveTransport(int slaveAddress, int[] holding) {
        this.slaveAddress = slaveAddress;
        this.holding = holding.clone();
    }

    /** 令应答 LRC 出错，用于验证校验拦截。 */
    void corruptLrc() {
        this.corruptLrc = true;
    }

    /** 令从站固定返回指定异常码。 */
    void forceException(int code) {
        this.forcedExceptionCode = code;
    }

    /** 收到的原始请求帧。 */
    List<byte[]> requests() {
        return new ArrayList<>(requests);
    }

    /** 当前保持寄存器值。 */
    int[] holding() {
        return holding.clone();
    }

    @Override
    public void open() {
        open = true;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void write(byte[] data) throws IOException {
        requests.add(data.clone());
        if (forcedExceptionCode <= 0) {
            // 请求必须能被被测解码器接受（含 LRC 校验）
            ModbusAsciiProtocol.decode(data);
        }
        byte[] response = this.respond(ModbusAsciiProtocol.decode(data));
        if (corruptLrc) {
            response[response.length - 4] = '0';
            response[response.length - 3] = '0';
        }
        for (byte value : response) {
            incoming.addLast(value & 0xFF);
        }
    }

    @Override
    public byte[] readExactly(int expectedLength, int timeoutMs) throws IOException {
        byte[] out = new byte[expectedLength];
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 50);
        for (int i = 0; i < expectedLength; i++) {
            while (incoming.isEmpty()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IOException("模拟 ASCII 从站无应答，读取超时");
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
        return "内存 ASCII 从站 " + slaveAddress;
    }

    @Override
    public void close() {
        open = false;
    }

    private byte[] respond(byte[] body) {
        if (body.length < 4) {
            throw new IllegalArgumentException("请求过短");
        }
        int slave = body[0] & 0xFF;
        int function = body[1] & 0xFF;
        if (slave != slaveAddress) {
            throw new IllegalArgumentException("从站地址不匹配");
        }
        if (forcedExceptionCode > 0) {
            return ModbusAsciiProtocol.encode(new byte[] {(byte) slave, (byte) (function | 0x80),
                (byte) forcedExceptionCode});
        }
        if (function == 0x03 || function == 0x04) {
            int start = ((body[2] & 0xFF) << 8) | (body[3] & 0xFF);
            int count = ((body[4] & 0xFF) << 8) | (body[5] & 0xFF);
            byte[] payload = new byte[3 + count * 2];
            payload[0] = (byte) slave;
            payload[1] = (byte) function;
            payload[2] = (byte) (count * 2);
            for (int i = 0; i < count; i++) {
                payload[3 + i * 2] = (byte) ((holding[start + i] >>> 8) & 0xFF);
                payload[4 + i * 2] = (byte) (holding[start + i] & 0xFF);
            }
            return ModbusAsciiProtocol.encode(payload);
        }
        if (function == 0x06) {
            int register = ((body[2] & 0xFF) << 8) | (body[3] & 0xFF);
            int value = ((body[4] & 0xFF) << 8) | (body[5] & 0xFF);
            if (register < holding.length) {
                holding[register] = value;
            }
            byte[] payload = new byte[6];
            System.arraycopy(body, 0, payload, 0, 6);
            return ModbusAsciiProtocol.encode(payload);
        }
        if (function == 0x10) {
            int start = ((body[2] & 0xFF) << 8) | (body[3] & 0xFF);
            int count = ((body[4] & 0xFF) << 8) | (body[5] & 0xFF);
            for (int i = 0; i < count; i++) {
                int value = ((body[7 + i * 2] & 0xFF) << 8) | (body[8 + i * 2] & 0xFF);
                if (start + i < holding.length) {
                    holding[start + i] = value;
                }
            }
            byte[] payload = new byte[6];
            System.arraycopy(body, 0, payload, 0, 6);
            return ModbusAsciiProtocol.encode(payload);
        }
        return ModbusAsciiProtocol.encode(new byte[] {(byte) slave, (byte) (function | 0x80), 1});
    }
}
