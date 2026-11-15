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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Modbus ASCII（FR-PROTO-04，可选协议）。
 *
 * <p>帧格式：`:` + 从站地址/功能码/数据的十六进制字符 + LRC + CRLF；LRC 为参与校验字节的
 * 二进制补码（单字节）。与 RTU 相比每字节占 2 个字符，因此这里用「字符数」推算应答长度：
 * 读应答 = 11 + 2×字节数，写应答（06/16 回显）= 17，异常应答 = 11。</p>
 *
 * <p>PDU 层与 RTU/TCP 完全复用（{@link ModbusPdu}），本类只负责 ASCII 组帧与解帧。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
public final class ModbusAsciiProtocol extends AbstractModbusProtocol {

    /** 起始符 */
    public static final byte START = ':';

    /** 结束符 */
    public static final byte[] CRLF = {0x0D, 0x0A};

    /** 十六进制字符表（现场设备与规格示例统一用大写） */
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** 读应答头部字符数：: + 地址2 + 功能码2 + 字节数2 */
    private static final int READ_HEAD_CHARS = 7;

    /** 异常应答总字符数：: + 地址2 + 功能码2 + 异常码2 + LRC2 + CRLF2 */
    private static final int EXCEPTION_CHARS = 11;

    /** 写应答（06/16 回显）总字符数：: + 地址2 + 功能码2 + 数据8 + LRC2 + CRLF2 */
    private static final int WRITE_RESPONSE_CHARS = 17;

    public ModbusAsciiProtocol(DeviceTransport transport, int timeoutMs, int retries) {
        super(transport, timeoutMs, retries);
    }

    @Override
    public String name() {
        return "Modbus ASCII";
    }

    @Override
    protected byte[] exchange(byte[] pdu, int slaveAddress, int functionCode) {
        byte[] payload = new byte[1 + pdu.length];
        payload[0] = (byte) slaveAddress;
        System.arraycopy(pdu, 0, payload, 1, pdu.length);
        byte[] request = encode(payload);
        try {
            transport.purge();
            transport.write(request);
            this.traceOut(slaveAddress, request);

            byte[] head = transport.readExactly(READ_HEAD_CHARS, this.currentTimeoutMs());
            int slave = parseHex(head, 1);
            int function = parseHex(head, 3);
            if (slave != slaveAddress) {
                this.traceError(slaveAddress, "从站地址不匹配，收到 " + slave);
                throw new ProtocolException("从站地址不匹配：期望 " + slaveAddress + "，实际 " + slave);
            }
            if ((function & 0x80) != 0) {
                byte[] frame = concatenate(head, transport.readExactly(EXCEPTION_CHARS
                    - READ_HEAD_CHARS, this.currentTimeoutMs()));
                this.traceIn(slaveAddress, frame);
                byte[] body = decode(frame);
                return new byte[] {(byte) function, body[2]};
            }
            int remaining = isReadFunction(function) ? 11 + 2 * parseHex(head,
                5) - READ_HEAD_CHARS : WRITE_RESPONSE_CHARS - READ_HEAD_CHARS;
            byte[] frame =
                concatenate(head, transport.readExactly(remaining, this.currentTimeoutMs()));
            this.traceIn(slaveAddress, frame);
            byte[] body = decode(frame);
            byte[] responsePdu = new byte[body.length - 1];
            System.arraycopy(body, 1, responsePdu, 0, responsePdu.length);
            return responsePdu;
        } catch (ProtocolException e) {
            throw e;
        } catch (Exception e) {
            this.traceError(slaveAddress, e.getMessage());
            throw new ProtocolException("ASCII 收发失败：" + e.getMessage(), e);
        }
    }

    /**
     * 组帧：`:` + 十六进制正文 + CRLF
     *
     * @param payload 从站地址 + PDU
     * @return ASCII 帧字节
     */
    public static byte[] encode(byte[] payload) {
        int check = lrc(payload);
        StringBuilder text = new StringBuilder(1 + (payload.length + 1) * 2 + 2);
        text.append((char) START);
        for (byte value : payload) {
            appendHex(text, value);
        }
        appendHex(text, (byte) check);
        text.append('\r').append('\n');
        return text.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * 解帧并校验 LRC
     *
     * @param frame ASCII 帧字节
     * @return 从站地址 + PDU（不含 LRC）
     */
    public static byte[] decode(byte[] frame) {
        if (frame == null || frame.length < EXCEPTION_CHARS) {
            throw new ProtocolException("ASCII 帧过短：" + (frame == null ? 0 : frame.length) + " 字节");
        }
        if (frame[0] != START) {
            throw new ProtocolException("ASCII 帧缺少起始符 ':'");
        }
        if (frame[frame.length - 2] != CRLF[0] || frame[frame.length - 1] != CRLF[1]) {
            throw new ProtocolException("ASCII 帧缺少结束符 CRLF");
        }
        int bodyLength = (frame.length - 3) / 2;
        if ((frame.length - 3) % 2 != 0 || bodyLength < 2) {
            throw new ProtocolException("ASCII 帧长度不合法：" + frame.length);
        }
        byte[] body = new byte[bodyLength];
        for (int i = 0; i < bodyLength; i++) {
            body[i] = (byte) parseHex(frame, 1 + i * 2);
        }
        int expected = lrc(Arrays.copyOf(body, body.length - 1));
        int actual = body[body.length - 1] & 0xFF;
        if (expected != actual) {
            throw new ProtocolException("LRC 校验失败：期望 " + Integer.toHexString(expected) + "，实际 "
                + Integer.toHexString(actual));
        }
        return Arrays.copyOf(body, body.length - 1);
    }

    /**
     * 计算 LRC（参与校验字节之和的二进制补码）
     *
     * @param data 参与校验的字节
     * @return LRC
     */
    public static int lrc(byte[] data) {
        int sum = 0;
        for (byte value : data) {
            sum = (sum + (value & 0xFF)) & 0xFF;
        }
        return (256 - sum) & 0xFF;
    }

    private static boolean isReadFunction(int functionCode) {
        return functionCode == ModbusPdu.FUNCTION_READ_HOLDING
            || functionCode == ModbusPdu.FUNCTION_READ_INPUT;
    }

    private static void appendHex(StringBuilder text, byte value) {
        text.append(HEX[(value >> 4) & 0xF]);
        text.append(HEX[value & 0xF]);
    }

    private static int parseHex(byte[] frame, int index) {
        return (digit(frame[index]) << 4) | digit(frame[index + 1]);
    }

    private static int digit(byte value) {
        int code = value & 0xFF;
        int digit = Character.digit(code, 16);
        if (digit < 0) {
            throw new ProtocolException("ASCII 帧含非十六进制字符：0x" + Integer.toHexString(code));
        }
        return digit;
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
