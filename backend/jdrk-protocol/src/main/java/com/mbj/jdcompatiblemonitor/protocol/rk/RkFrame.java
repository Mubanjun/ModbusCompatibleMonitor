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

/**
 * 仁科 2.0 网络设备（2404 端口）的协议帧。
 *
 * <pre>
 * 68 | 6 字节地址 | 68 | 命令 | 长度 | 数据[长度] | 校验 | 16
 * 总长度   = 长度 + 12
 * 校验     = 数据区之前全部字节累加后取低 8 位
 * 命令与数据字节按 +0x33 偏移存储
 * </pre>
 */
public record RkFrame(byte[] raw, int lengthField, byte[] payload, int checksum,
    boolean checksumValid) {

    public static final int HEADER_LENGTH = 10;
    public static final int TAIL_LENGTH = 2;
    public static final int OFFSET = 0x33;

    public int frameByte() {
        return raw[0] & 0xFF;
    }

    public int payloadTypeByte() {
        return raw[8] & 0xFF;
    }

    /** 0xA0 = 设备上报，0x20 = 平台应答。 */
    public boolean isDeviceReport() {
        return payloadTypeByte() == 0xA0;
    }

    public boolean isPlatformAck() {
        return payloadTypeByte() == 0x20;
    }

    /** 还原 2 字节命令码，例如 EA03（实时数据）、EA0A（已存储数据）。 */
    public String commandCode() {
        int first = (payload[1] - OFFSET) & 0xFF;
        int second = (payload[0] - OFFSET) & 0xFF;
        return Hex.toHex(new byte[] {(byte) first, (byte) second});
    }

    /** 帧尾 4 字节设备地址。 */
    public byte[] deviceAddress() {
        return new byte[] {
            raw[raw.length - 6], raw[raw.length - 5], raw[raw.length - 4], raw[raw.length - 3]
        };
    }

    public int totalLength() {
        return raw.length;
    }
}
