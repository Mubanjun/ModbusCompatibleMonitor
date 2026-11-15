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

/**
 * 仁科水质传感器的数值编码方式。
 *
 * <p>寄存器语义来自厂家 485 型变送器说明书；水质要素在不同量程下缩放不同，
 * 因此编码必须显式建模，不能写死。</p>
 */
public enum ValueEncoding {

    UINT16_SCALE_1("16位无符号整数，实际值", 1, false, 1.0D),
    UINT16_SCALE_10("16位无符号整数，实际值的10倍", 1, false, 10.0D),
    UINT16_SCALE_100("16位无符号整数，实际值的100倍", 1, false, 100.0D),
    UINT16_SCALE_1000("16位无符号整数，实际值的1000倍", 1, false, 1000.0D),
    INT16_SCALE_1("16位有符号整数，实际值", 1, true, 1.0D),
    INT16_SCALE_10("16位有符号整数，实际值的10倍", 1, true, 10.0D),
    INT16_SCALE_100("16位有符号整数，实际值的100倍", 1, true, 100.0D),
    UINT32_SCALE_1("32位无符号整数，实际值", 2, false, 1.0D),
    UINT32_SCALE_10("32位无符号整数，实际值的10倍", 2, false, 10.0D),
    UINT32_SCALE_100("32位无符号整数，实际值的100倍", 2, false, 100.0D),
    FLOAT32_BIG_ENDIAN("32位大端浮点，实际值", 2, false, 1.0D);

    private final String description;
    private final int registerCount;
    private final boolean signed;
    private final double divisor;

    ValueEncoding(String description, int registerCount, boolean signed, double divisor) {
        this.description = description;
        this.registerCount = registerCount;
        this.signed = signed;
        this.divisor = divisor;
    }

    public String description() {
        return description;
    }

    public int registerCount() {
        return registerCount;
    }

    public boolean signed() {
        return signed;
    }

    public double divisor() {
        return divisor;
    }

    public boolean isFloat() {
        return this == FLOAT32_BIG_ENDIAN;
    }

    /** 主机通道参数里的系数 A（工程值 = 原始值 × A + B），即 1 ÷ 缩放倍数。 */
    public double hostCoefficientA() {
        return 1.0D / divisor;
    }

    /**
     * 只取寄存器里的原始数值（整数或浮点），不做倍数还原。
     *
     * <p>主机的通道系数 A 已经等于 1 ÷ 倍数，因此工程值应基于本方法的结果再乘 A，
     * 不能再叠加 divisor，否则会重复缩放。</p>
     */
    public double decodeRaw(int[] registers, int offset) {
        if (offset < 0 || offset + registerCount > registers.length) {
            throw new IllegalArgumentException("not enough registers: need "
                + registerCount + " at " + offset + ", got " + registers.length);
        }
        if (this == FLOAT32_BIG_ENDIAN) {
            int bits = ((registers[offset] & 0xFFFF) << 16) | (registers[offset + 1] & 0xFFFF);
            return Float.intBitsToFloat(bits);
        }
        if (registerCount == 2) {
            return ((long) (registers[offset] & 0xFFFF) << 16) | (registers[offset + 1] & 0xFFFF);
        }
        int value = registers[offset] & 0xFFFF;
        return signed ? (short) value : value;
    }

    /**
     * 按厂家说明书口径把寄存器还原为实际值（原始数值 ÷ 倍数）。
     *
     * @param registers 寄存器值（每项为 16 位，取值范围 0~65535）
     * @param offset    起始下标
     */
    public double decode(int[] registers, int offset) {
        if (offset < 0 || offset + registerCount > registers.length) {
            throw new IllegalArgumentException("not enough registers: need "
                + registerCount + " at " + offset + ", got " + registers.length);
        }
        if (this == FLOAT32_BIG_ENDIAN) {
            int bits = ((registers[offset] & 0xFFFF) << 16) | (registers[offset + 1] & 0xFFFF);
            return Float.intBitsToFloat(bits);
        }
        if (registerCount == 2) {
            long raw =
                ((long) (registers[offset] & 0xFFFF) << 16) | (registers[offset + 1] & 0xFFFF);
            return raw / divisor;
        }
        int value = registers[offset] & 0xFFFF;
        if (signed) {
            value = (short) value;
        }
        return value / divisor;
    }

    /** 工程值换算：原始值 × A + B。 */
    public static double toEngineering(double rawValue, double coefficientA, double coefficientB) {
        return rawValue * coefficientA + coefficientB;
    }
}
