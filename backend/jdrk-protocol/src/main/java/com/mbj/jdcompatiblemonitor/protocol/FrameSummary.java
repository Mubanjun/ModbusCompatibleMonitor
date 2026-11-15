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

import java.util.StringJoiner;

/**
 * 把一帧 Modbus 报文翻译为可读摘要，供信令监视展示解析结果（FR-SIG-01）。
 *
 * <p>只做展示用途的宽松解析：报文不完整时退化为字节数描述，绝不抛异常，
 * 否则信令采集本身会影响采集主流程（FR-SIG-05）。</p>
 */
public final class FrameSummary {

    /** TCP 报文的 MBAP 头长度 */
    private static final int MBAP_LENGTH = 7;
    /** 摘要里最多列出的寄存器个数 */
    private static final int MAX_REGISTERS = 16;

    private FrameSummary() {
    }

    /**
     * 生成报文摘要
     *
     * @param tcp       是否为 Modbus TCP 帧
     * @param outbound  是否为发送方向
     * @param frame     报文字节
     * @return 摘要文本
     */
    public static String describe(boolean tcp, boolean outbound, byte[] frame) {
        if (frame == null || frame.length == 0) {
            return "空报文";
        }
        int offset = tcp ? MBAP_LENGTH : 1;
        String prefix = outbound ? "发送" : "接收";
        // 请求帧至少要有功能码 + 4 字节数据，应答帧至少要有功能码；不足则退化为字节数描述
        int minimum = offset + (outbound ? 5 : 1);
        if (frame.length < minimum) {
            return prefix + " " + frame.length + " 字节";
        }
        int function = frame[offset] & 0xFF;
        String body;
        try {
            body = outbound ? describeRequest(function, frame, offset)
                : describeResponse(function, frame, offset);
        } catch (RuntimeException e) {
            body = "解析失败";
        }
        if (body == null) {
            body = "功能码 0x" + Integer.toHexString(function) + " " + (frame.length - offset)
                + " 字节";
        }
        return prefix + " " + body;
    }

    /** 请求方向摘要。 */
    private static String describeRequest(int function, byte[] frame, int offset) {
        return switch (function) {
            case ModbusPdu.FUNCTION_READ_HOLDING -> "读保持寄存器 起始=" + register(frame, offset + 1)
                + " 数量=" + register(frame, offset + 3);
            case ModbusPdu.FUNCTION_READ_INPUT -> "读输入寄存器 起始=" + register(frame, offset + 1)
                + " 数量=" + register(frame, offset + 3);
            case ModbusPdu.FUNCTION_WRITE_SINGLE -> "写单寄存器 地址=" + register(frame, offset + 1)
                + " 值=" + register(frame, offset + 3);
            case ModbusPdu.FUNCTION_WRITE_MULTIPLE -> "写多寄存器 起始=" + register(frame, offset + 1)
                + " 数量=" + register(frame, offset + 3);
            default -> null;
        };
    }

    /** 应答方向摘要。 */
    private static String describeResponse(int function, byte[] frame, int offset) {
        if ((function & 0x80) != 0) {
            int code = frame[offset + 1] & 0xFF;
            return "异常应答 功能码=0x" + Integer.toHexString(function) + " 异常码=" + code + "("
                + ModbusPdu.describeException(code) + ")";
        }
        return switch (function) {
            case ModbusPdu.FUNCTION_READ_HOLDING, ModbusPdu.FUNCTION_READ_INPUT -> {
                // 解析成功时列出寄存器；报文不完整时直接给出原因，避免出现「寄存器=长度不足」
                String parsed = registers(frame, offset);
                yield parsed.startsWith("[") ? "寄存器=" + parsed : parsed;
            }
            case ModbusPdu.FUNCTION_WRITE_SINGLE -> "写单寄存器应答 地址=" + register(frame, offset + 1)
                + " 值=" + register(frame, offset + 3);
            case ModbusPdu.FUNCTION_WRITE_MULTIPLE -> "写多寄存器应答 起始="
                + register(frame, offset + 1) + " 数量=" + register(frame, offset + 3);
            default -> null;
        };
    }

    /** 提取读应答里的寄存器列表。 */
    private static String registers(byte[] frame, int offset) {
        if (frame.length <= offset + 1) {
            return "长度不足";
        }
        int byteCount = frame[offset + 1] & 0xFF;
        if (frame.length < offset + 2 + byteCount) {
            return "长度不足（声明 " + byteCount + " 字节）";
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        int count = byteCount / 2;
        for (int i = 0; i < Math.min(count, MAX_REGISTERS); i++) {
            joiner.add(String.valueOf(register(frame, offset + 2 + i * 2)));
        }
        if (count > MAX_REGISTERS) {
            joiner.add("... 共 " + count + " 个");
        }
        return joiner.toString();
    }

    /** 读取一个大端 16 位寄存器。 */
    private static int register(byte[] frame, int index) {
        if (index < 0 || index + 1 >= frame.length) {
            return -1;
        }
        return ((frame[index] & 0xFF) << 8) | (frame[index + 1] & 0xFF);
    }

    /**
     * 安全地把报文转为十六进制文本
     *
     * @param frame 报文
     * @return 十六进制文本，超长时截断
     */
    public static String toHex(byte[] frame) {
        if (frame == null || frame.length == 0) {
            return "";
        }
        String hex = Hex.toHex(frame, " ");
        return hex.length() > 1000 ? hex.substring(0, 1000) + " ..." : hex;
    }
}
