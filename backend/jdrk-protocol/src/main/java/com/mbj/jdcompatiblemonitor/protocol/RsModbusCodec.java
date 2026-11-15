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

/**
 * 仁科主机 RS-Modbus 从站接口的编解码。
 *
 * <p>通讯参数：8N1、CRC16、从站地址等于通道号（1~32）。
 * 寄存器映射：0x0000 = 模拟量2，0x0001 = 模拟量1。</p>
 */
public final class RsModbusCodec {

    public static final int FUNCTION_READ_HOLDING = 0x03;
    public static final int FUNCTION_READ_INPUT = 0x04;
    public static final int FUNCTION_WRITE_SINGLE = 0x06;
    public static final int FUNCTION_WRITE_MULTIPLE = 0x10;

    /** 寄存器 0x0000 对应模拟量2。 */
    public static final int REGISTER_ANALOG2 = 0x0000;

    /** 寄存器 0x0001 对应模拟量1。 */
    public static final int REGISTER_ANALOG1 = 0x0001;

    private RsModbusCodec() {
    }

    /** 组装读寄存器请求帧，自动追加 CRC16。 */
    public static byte[] buildReadRequest(int slaveAddress, int functionCode, int startRegister,
        int registerCount) {
        if (slaveAddress < 1 || slaveAddress > 255) {
            throw new IllegalArgumentException("slave address out of range: " + slaveAddress);
        }
        if (registerCount < 1 || registerCount > 125) {
            throw new IllegalArgumentException("register count out of range: " + registerCount);
        }
        byte[] payload = new byte[] {
            (byte) slaveAddress,
            (byte) functionCode,
            (byte) ((startRegister >>> 8) & 0xFF),
            (byte) (startRegister & 0xFF),
            (byte) ((registerCount >>> 8) & 0xFF),
            (byte) (registerCount & 0xFF)
        };
        return Crc16Modbus.withCrc(payload);
    }

    /**
     * 解析读寄存器应答。
     *
     * @return 寄存器数组（每项 0~65535）
     */
    public static int[] parseReadResponse(byte[] frame, int expectedSlave, int expectedFunction) {
        if (frame == null || frame.length < 5) {
            throw new IllegalArgumentException(
                "frame too short: " + Hex.toHex(frame == null ? new byte[0] : frame));
        }
        if (!Crc16Modbus.verify(frame, frame.length)) {
            throw new IllegalArgumentException("CRC check failed: " + Hex.toHex(frame));
        }
        return parseWithoutCrc(frame, expectedSlave, expectedFunction);
    }

    /** 已知长度时按有效长度校验（串口可能读到多余字节）。 */
    public static int[] parseReadResponse(byte[] buffer, int length, int expectedSlave,
        int expectedFunction) {
        if (buffer == null || length < 5) {
            throw new IllegalArgumentException("response too short");
        }
        if (!Crc16Modbus.verify(buffer, length)) {
            throw new IllegalArgumentException(
                "CRC check failed: " + Hex.toHex(buffer, 0, length, " "));
        }
        return parseWithoutCrc(java.util.Arrays.copyOf(buffer, length), expectedSlave,
            expectedFunction);
    }

    private static int[] parseWithoutCrc(byte[] frame, int expectedSlave, int expectedFunction) {
        int slave = frame[0] & 0xFF;
        if (slave != expectedSlave) {
            throw new IllegalArgumentException("unexpected slave: " + slave);
        }
        int function = frame[1] & 0xFF;
        if ((function & 0x80) != 0) {
            throw new IllegalStateException("device returned exception code 0x"
                + Hex.toHex(new byte[] {(byte) frame[2]}));
        }
        if (function != expectedFunction) {
            throw new IllegalArgumentException("unexpected function: " + function);
        }
        int byteCount = frame[2] & 0xFF;
        if (frame.length < 3 + byteCount + 2) {
            throw new IllegalArgumentException("truncated response: " + Hex.toHex(frame));
        }
        int[] registers = new int[byteCount / 2];
        for (int i = 0; i < registers.length; i++) {
            registers[i] = ((frame[3 + i * 2] & 0xFF) << 8) | (frame[4 + i * 2] & 0xFF);
        }
        return registers;
    }

    /** 期望的应答总长度（用于串口读取）。 */
    public static int expectedResponseLength(int registerCount) {
        return 5 + registerCount * 2;
    }
}
