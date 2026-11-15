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

package com.mbj.jdcompatiblemonitor.common;

/** Modbus RTU 的 CRC16（多项式 0xA001，低字节在前）。 */
public final class Crc16Modbus {

    private static final int POLYNOMIAL = 0xA001;

    private Crc16Modbus() {
    }

    /** 计算整段报文的 Modbus CRC16。 */
    public static int compute(byte[] data) {
        return compute(data, 0, data.length);
    }

    /** 计算指定区间的 Modbus CRC16。 */
    public static int compute(byte[] data, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= data[offset + i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ POLYNOMIAL;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /** 在报文尾部追加 CRC 低字节、高字节。 */
    public static byte[] withCrc(byte[] payload) {
        byte[] out = new byte[payload.length + 2];
        System.arraycopy(payload, 0, out, 0, payload.length);
        int crc = compute(payload);
        out[payload.length] = (byte) (crc & 0xFF);
        out[payload.length + 1] = (byte) ((crc >>> 8) & 0xFF);
        return out;
    }

    /** 校验前 length 个字节（最后两字节为 CRC）。 */
    public static boolean verify(byte[] frame, int length) {
        if (length < 3 || frame.length < length) {
            return false;
        }
        int expected = (frame[length - 2] & 0xFF) | ((frame[length - 1] & 0xFF) << 8);
        return compute(frame, 0, length - 2) == expected;
    }
}
