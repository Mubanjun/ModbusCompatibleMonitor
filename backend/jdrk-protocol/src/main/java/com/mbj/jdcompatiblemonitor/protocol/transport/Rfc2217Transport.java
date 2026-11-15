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

package com.mbj.jdcompatiblemonitor.protocol.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RFC2217 串口服务器传输（Telnet COM-PORT-OPTION）。 */
public final class Rfc2217Transport implements DeviceTransport {

    private static final Logger LOG = LoggerFactory.getLogger(Rfc2217Transport.class);
    private static final int IAC = 255;
    private static final int DONT = 254;
    private static final int DO = 253;
    private static final int WONT = 252;
    private static final int WILL = 251;
    private static final int SB = 250;
    private static final int SE = 240;
    private static final int COM_PORT_OPTION = 44;
    private static final int SET_BAUDRATE = 1;
    private static final int SET_DATASIZE = 2;
    private static final int SET_PARITY = 3;
    private static final int SET_STOPSIZE = 4;
    private static final int SET_CONTROLS = 5;
    private static final int NOTIFY_LINESTATE = 10;
    private static final int NOTIFY_MODEMSTATE = 11;

    private final String host;
    private final int port;
    private final int baudRate;
    private final int dataBits;
    private final int stopBits;
    private final int parity;
    private final int flowControl;
    private final int connectTimeoutMs;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private final ByteArrayOutputStream decoded = new ByteArrayOutputStream();
    private volatile boolean linkBroken;
    private volatile String lastWarning = "";

    public Rfc2217Transport(String host, int port, int baudRate, int dataBits, int stopBits,
        int parity,
        int flowControl, int connectTimeoutMs) {
        this.host = host;
        this.port = port;
        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.parity = parity;
        this.flowControl = flowControl;
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /** 从 host:port 地址构造传输实例。 */
    public static Rfc2217Transport parse(String address, int baudRate, int dataBits, int stopBits,
        int parity,
        int flowControl, int timeoutMs) {
        int idx = address.lastIndexOf(':');
        if (idx <= 0 || idx == address.length() - 1) {
            throw new IllegalArgumentException("RFC2217 地址格式应为 host:port，实际为：" + address);
        }
        return new Rfc2217Transport(address.substring(0, idx),
            Integer.parseInt(address.substring(idx + 1).trim()),
            baudRate, dataBits, stopBits, parity, flowControl, timeoutMs);
    }

    /** 打开连接并完成 RFC2217 选项协商。 */
    @Override
    public void open() throws IOException {
        close();
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        s.setTcpNoDelay(true);
        s.setSoTimeout(Math.max(200, connectTimeoutMs));
        this.socket = s;
        this.in = s.getInputStream();
        this.out = s.getOutputStream();
        this.decoded.reset();
        this.linkBroken = false;
        writeTelnet(new int[] {IAC, WILL, COM_PORT_OPTION});
        drain(250);
        writeOption(SET_BAUDRATE, intToBytes(baudRate));
        writeOption(SET_DATASIZE, new byte[] {(byte) dataBits});
        writeOption(SET_PARITY, new byte[] {(byte) parity});
        writeOption(SET_STOPSIZE, new byte[] {(byte) stopBits});
        writeOption(SET_CONTROLS, new byte[] {(byte) flowControl});
        drain(400);
    }

    @Override
    public boolean isOpen() {
        return socket != null && socket.isConnected() && !socket.isClosed() && !linkBroken;
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("RFC2217 通道未打开：" + description());
        }
        ByteArrayOutputStream escaped = new ByteArrayOutputStream(data.length + 8);
        for (byte b : data) {
            escaped.write(b & 0xFF);
            if ((b & 0xFF) == IAC) {
                escaped.write(IAC);
            }
        }
        out.write(escaped.toByteArray());
        out.flush();
    }

    @Override
    public byte[] readExactly(int expectedLength, int timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (decoded.size() < expectedLength) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new IOException(
                    "RFC2217 读取超时：期望 " + expectedLength + " 字节，实际 " + decoded.size());
            }
            if (!pumpOne((int) Math.min(remaining, Math.max(50, connectTimeoutMs)))) {
                DeviceTransport.sleepQuietly(2);
            }
        }
        byte[] all = decoded.toByteArray();
        byte[] result = Arrays.copyOfRange(all, 0, expectedLength);
        decoded.reset();
        decoded.write(all, expectedLength, all.length - expectedLength);
        return result;
    }

    @Override
    public void purge() throws IOException {
        decoded.reset();
        if (in == null) {
            return;
        }
        while (in.available() > 0) {
            pumpOne(5);
        }
    }

    private boolean pumpOne(int timeoutMs) throws IOException {
        if (in == null) {
            throw new IOException("RFC2217 通道未打开：" + description());
        }
        socket.setSoTimeout(Math.max(1, timeoutMs));
        int first = in.read();
        if (first < 0) {
            linkBroken = true;
            throw new IOException("RFC2217 服务端关闭连接");
        }
        if (first != IAC) {
            decoded.write(first);
            return true;
        }
        int command = in.read();
        if (command < 0) {
            linkBroken = true;
            throw new IOException("RFC2217 Telnet 命令不完整");
        }
        if (command == IAC) {
            decoded.write(IAC);
            return true;
        }
        if (command == WILL || command == WONT || command == DO || command == DONT) {
            int option = in.read();
            negotiate(command, option);
            return false;
        }
        if (command == SB) {
            handleSubnegotiation(readSubnegotiation());
            return false;
        }
        return false;
    }

    private byte[] readSubnegotiation() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int value = in.read();
            if (value < 0) {
                throw new IOException("RFC2217 子协商不完整");
            }
            if (previous == IAC && value == SE) {
                break;
            }
            if (previous == IAC && value == IAC) {
                payload.write(IAC);
                previous = -1;
                continue;
            }
            if (previous >= 0) {
                payload.write(previous);
            }
            previous = value;
        }
        return payload.toByteArray();
    }

    private void negotiate(int command, int option) throws IOException {
        if (option == COM_PORT_OPTION) {
            if (command == DO) {
                writeTelnet(new int[] {IAC, WILL, COM_PORT_OPTION});
            } else if (command == WILL) {
                writeTelnet(new int[] {IAC, DO, COM_PORT_OPTION});
            } else {
                writeTelnet(new int[] {IAC, WONT, COM_PORT_OPTION});
            }
            return;
        }
        writeTelnet(new int[] {IAC, command == DO ? WONT : DONT, option});
    }

    private void handleSubnegotiation(byte[] payload) {
        if (payload.length < 2 || (payload[0] & 0xFF) != COM_PORT_OPTION) {
            return;
        }
        int command = payload[1] & 0xFF;
        if (command == NOTIFY_LINESTATE || command == NOTIFY_MODEMSTATE) {
            LOG.debug("RFC2217 控制线通知 {} = {}", command,
                payload.length > 2 ? payload[2] & 0xFF : -1);
            return;
        }
        if (command == SET_BAUDRATE && payload.length >= 6) {
            int actual = ((payload[2] & 0xFF) << 24) | ((payload[3] & 0xFF) << 16)
                | ((payload[4] & 0xFF) << 8) | (payload[5] & 0xFF);
            if (actual != baudRate) {
                lastWarning = "RFC2217 服务端回读波特率 " + actual + " 与配置 " + baudRate + " 不一致";
                LOG.warn(lastWarning);
            }
        }
    }

    private void drain(long millis) throws IOException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (in.available() > 0) {
                pumpOne(20);
            } else {
                DeviceTransport.sleepQuietly(5);
            }
        }
    }

    private void writeOption(int command, byte[] value) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(value.length + 8);
        frame.write(IAC);
        frame.write(SB);
        frame.write(COM_PORT_OPTION);
        frame.write(command);
        frame.write(value);
        frame.write(IAC);
        frame.write(SE);
        out.write(frame.toByteArray());
        out.flush();
    }

    @Override
    public String description() {
        return "RFC2217 " + host + ":" + port;
    }

    @Override
    public void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 关闭失败无需处理
            }
        }
        socket = null;
        in = null;
        out = null;
        decoded.reset();
    }

    public String lastWarning() {
        return lastWarning;
    }

    private void writeTelnet(int[] bytes) throws IOException {
        for (int value : bytes) {
            out.write(value & 0xFF);
        }
        out.flush();
    }

    private static byte[] intToBytes(int value) {
        return new byte[] {(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8),
            (byte) value};
    }
}
