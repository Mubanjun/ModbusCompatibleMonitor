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

package com.mbj.jdcompatiblemonitor.protocol.rk;

import com.mbj.jdcompatiblemonitor.common.Hex;

/** 仁科 2404 帧的编解码与切分。 */
public final class RkFrameCodec {

    private RkFrameCodec() {
    }

    /** 计算累加校验和，按单字节溢出。 */
    public static int checksum(byte[] data, int offset, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum = (sum + data[offset + i]) & 0xFF;
        }
        return sum;
    }

    /** 按长度字段切出完整帧的应有字节数；返回 -1 表示数据不足。 */
    public static int frameLength(byte[] buffer, int offset) {
        if (buffer.length - offset < RkFrame.HEADER_LENGTH) {
            return -1;
        }
        if ((buffer[offset] & 0xFF) != 0x68 || (buffer[offset + 7] & 0xFF) != 0x68) {
            return -2;
        }
        return (buffer[offset + 9] & 0xFF) + RkFrame.HEADER_LENGTH + RkFrame.TAIL_LENGTH;
    }

    /** 解析一帧，并校验长度、帧分隔符、累加校验和与帧尾。 */
    public static RkFrame parse(byte[] raw) {
        if (raw.length < RkFrame.HEADER_LENGTH + RkFrame.TAIL_LENGTH) {
            throw new IllegalArgumentException("frame too short: " + Hex.toHex(raw));
        }
        if ((raw[0] & 0xFF) != 0x68 || (raw[7] & 0xFF) != 0x68) {
            throw new IllegalArgumentException("bad frame delimiter: " + Hex.toHex(raw));
        }
        int lengthField = raw[9] & 0xFF;
        int expected = lengthField + RkFrame.HEADER_LENGTH + RkFrame.TAIL_LENGTH;
        if (raw.length != expected) {
            throw new IllegalArgumentException(
                "length mismatch: declared " + expected + " but got " + raw.length);
        }
        byte[] payload = new byte[lengthField];
        System.arraycopy(raw, RkFrame.HEADER_LENGTH, payload, 0, lengthField);
        int checksum = raw[lengthField + RkFrame.HEADER_LENGTH] & 0xFF;
        boolean valid = checksum == checksum(raw, 0, lengthField + RkFrame.HEADER_LENGTH)
            && (raw[lengthField + RkFrame.HEADER_LENGTH + 1] & 0xFF) == 0x16;
        return new RkFrame(raw, lengthField, payload, checksum, valid);
    }

    /** 还原 +0x33 偏移。 */
    public static byte[] decodeOffset(byte[] data) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) ((data[i] - RkFrame.OFFSET) & 0xFF);
        }
        return out;
    }

    /** 施加 +0x33 偏移。 */
    public static byte[] encodeOffset(byte[] data) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) ((data[i] + RkFrame.OFFSET) & 0xFF);
        }
        return out;
    }

    /** 组装一帧（payload 需已按 +0x33 偏移编码）。 */
    public static byte[] build(byte[] address6, int commandByte, byte[] payload) {
        if (address6.length != 6) {
            throw new IllegalArgumentException("address must be 6 bytes");
        }
        if (payload.length > 255) {
            throw new IllegalArgumentException("payload too long: " + payload.length);
        }
        byte[] frame = new byte[RkFrame.HEADER_LENGTH + payload.length + RkFrame.TAIL_LENGTH];
        frame[0] = 0x68;
        System.arraycopy(address6, 0, frame, 1, 6);
        frame[7] = 0x68;
        frame[8] = (byte) commandByte;
        frame[9] = (byte) payload.length;
        System.arraycopy(payload, 0, frame, RkFrame.HEADER_LENGTH, payload.length);
        frame[frame.length - 2] = (byte) checksum(frame, 0, RkFrame.HEADER_LENGTH + payload.length);
        frame[frame.length - 1] = 0x16;
        return frame;
    }
}
