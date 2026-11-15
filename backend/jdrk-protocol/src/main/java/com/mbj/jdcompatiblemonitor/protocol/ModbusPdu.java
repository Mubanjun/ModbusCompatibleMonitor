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

import com.mbj.jdcompatiblemonitor.common.Hex;

/**
 * Modbus PDU 组装与解析。
 *
 * <p>PDU 不含从站地址与校验，RTU 与 TCP 共用同一套 PDU 逻辑：
 * RTU 在 PDU 外包 从站地址 + CRC16，TCP 则包 MBAP 报文头。</p>
 */
public final class ModbusPdu {

    /** 读保持寄存器 */
    public static final int FUNCTION_READ_HOLDING = 0x03;
    /** 读输入寄存器 */
    public static final int FUNCTION_READ_INPUT = 0x04;
    /** 写单个寄存器 */
    public static final int FUNCTION_WRITE_SINGLE = 0x06;
    /** 写多个寄存器 */
    public static final int FUNCTION_WRITE_MULTIPLE = 0x10;

    private ModbusPdu() {
    }

    /**
     * 组装读寄存器请求 PDU
     *
     * @param functionCode  功能码（03 或 04）
     * @param startRegister 起始寄存器
     * @param registerCount 寄存器数量
     * @return PDU 字节
     */
    public static byte[] readRequest(int functionCode, int startRegister, int registerCount) {
        if (registerCount < 1 || registerCount > 125) {
            throw new IllegalArgumentException("寄存器数量超出范围：" + registerCount);
        }
        return new byte[] {
            (byte) functionCode,
            (byte) ((startRegister >>> 8) & 0xFF),
            (byte) (startRegister & 0xFF),
            (byte) ((registerCount >>> 8) & 0xFF),
            (byte) (registerCount & 0xFF)
        };
    }

    /**
     * 组装写单个寄存器请求 PDU
     *
     * @param register 寄存器地址
     * @param value    写入值
     * @return PDU 字节
     */
    public static byte[] writeSingleRequest(int register, int value) {
        return new byte[] {
            (byte) FUNCTION_WRITE_SINGLE,
            (byte) ((register >>> 8) & 0xFF),
            (byte) (register & 0xFF),
            (byte) ((value >>> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }

    /**
     * 组装写多个寄存器请求 PDU
     *
     * @param startRegister 起始寄存器
     * @param values        写入值
     * @return PDU 字节
     */
    public static byte[] writeMultipleRequest(int startRegister, int[] values) {
        if (values == null || values.length == 0 || values.length > 123) {
            throw new IllegalArgumentException("写入寄存器数量超出范围");
        }
        byte[] pdu = new byte[6 + values.length * 2];
        pdu[0] = (byte) FUNCTION_WRITE_MULTIPLE;
        pdu[1] = (byte) ((startRegister >>> 8) & 0xFF);
        pdu[2] = (byte) (startRegister & 0xFF);
        pdu[3] = (byte) ((values.length >>> 8) & 0xFF);
        pdu[4] = (byte) (values.length & 0xFF);
        pdu[5] = (byte) (values.length * 2);
        for (int i = 0; i < values.length; i++) {
            pdu[6 + i * 2] = (byte) ((values[i] >>> 8) & 0xFF);
            pdu[7 + i * 2] = (byte) (values[i] & 0xFF);
        }
        return pdu;
    }

    /**
     * 解析读寄存器应答 PDU
     *
     * @param pdu              应答 PDU
     * @param expectedFunction 期望功能码
     * @return 寄存器数组
     */
    public static int[] parseReadResponse(byte[] pdu, int expectedFunction) {
        checkException(pdu, expectedFunction);
        int byteCount = pdu[1] & 0xFF;
        if (pdu.length < 2 + byteCount) {
            throw new ProtocolException("应答长度不足：声明 " + byteCount + " 字节，实际 "
                + (pdu.length - 2) + " 字节");
        }
        int[] registers = new int[byteCount / 2];
        for (int i = 0; i < registers.length; i++) {
            registers[i] = ((pdu[2 + i * 2] & 0xFF) << 8) | (pdu[3 + i * 2] & 0xFF);
        }
        return registers;
    }

    /**
     * 解析读寄存器应答 PDU，并校验寄存器数量与请求一致。
     *
     * <p>485 半双工总线上若上一帧的应答迟到，会被本次请求读到；此时功能码与 CRC 都可能
     * 正常，只有字节数能暴露错位。若不校验，错误应答会被当成通道值写入实时值——数据静默
     * 出错比通讯失败更危险，因此这里必须拒绝并交给上层重试。</p>
     *
     * @param pdu              应答 PDU
     * @param expectedFunction 期望功能码
     * @param expectedCount    请求的寄存器数量
     * @return 寄存器数组
     */
    public static int[] parseReadResponse(byte[] pdu, int expectedFunction, int expectedCount) {
        int[] registers = parseReadResponse(pdu, expectedFunction);
        if (registers.length != expectedCount) {
            throw new ProtocolException("应答寄存器数量与请求不符：请求 " + expectedCount + " 个，实际 "
                + registers.length + " 个（字节数 " + (pdu[1] & 0xFF) + "），报文 " + Hex.toHex(pdu, " ")
                + "，可能是上一帧应答迟到");
        }
        return registers;
    }

    /**
     * 校验应答是否为异常响应
     *
     * @param pdu              应答 PDU
     * @param expectedFunction 期望功能码
     */
    public static void checkException(byte[] pdu, int expectedFunction) {
        if (pdu == null || pdu.length < 2) {
            throw new ProtocolException("应答过短：" + Hex.toHex(pdu == null ? new byte[0] : pdu));
        }
        int function = pdu[0] & 0xFF;
        if ((function & 0x80) != 0) {
            int code = pdu[1] & 0xFF;
            throw new ProtocolException("设备返回异常响应，功能码 0x" + Integer.toHexString(function)
                + "，异常码 " + code + "（" + describeException(code) + "）");
        }
        if (function != expectedFunction) {
            throw new ProtocolException("功能码不匹配：期望 " + expectedFunction + "，实际 " + function);
        }
    }

    /**
     * 异常码说明
     *
     * @param code 异常码
     * @return 说明
     */
    public static String describeException(int code) {
        return switch (code) {
            case 1 -> "非法功能码";
            case 2 -> "非法数据地址";
            case 3 -> "非法数据值";
            case 4 -> "从站设备故障";
            case 5 -> "确认";
            case 6 -> "从站设备忙";
            case 8 -> "存储奇偶性差错";
            default -> "未知异常码";
        };
    }
}
