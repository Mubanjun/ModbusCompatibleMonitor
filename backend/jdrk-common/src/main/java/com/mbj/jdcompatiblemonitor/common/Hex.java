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

/** 十六进制与字节数组互转。 */
public final class Hex {

    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();

    private Hex() {
    }

    /** 转为大写十六进制字符串，字节之间无分隔符。 */
    public static String toHex(byte[] data) {
        return toHex(data, 0, data.length, "");
    }

    /** 转为大写十六进制字符串，字节之间用指定分隔符连接。 */
    public static String toHex(byte[] data, String separator) {
        return toHex(data, 0, data.length, separator);
    }

    /** 转为大写十六进制字符串，可指定起始偏移与长度。 */
    public static String toHex(byte[] data, int offset, int length, String separator) {
        StringBuilder sb = new StringBuilder(length * (2 + separator.length()));
        for (int i = 0; i < length; i++) {
            if (i > 0 && !separator.isEmpty()) {
                sb.append(separator);
            }
            int value = data[offset + i] & 0xFF;
            sb.append(DIGITS[value >>> 4]).append(DIGITS[value & 0x0F]);
        }
        return sb.toString();
    }

    /** 解析十六进制字符串，忽略其中的非十六进制字符。 */
    public static byte[] fromHex(String text) {
        String normalized = text.replaceAll("[^0-9A-Fa-f]", "");
        if ((normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("hex string length must be even: " + text);
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
