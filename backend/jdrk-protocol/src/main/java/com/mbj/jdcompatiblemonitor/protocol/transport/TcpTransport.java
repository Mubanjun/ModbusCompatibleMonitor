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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP 传输。
 *
 * <p>用于 Modbus TCP，以及串口服务器（485 转以太网）场景。</p>
 */
public final class TcpTransport implements DeviceTransport {

    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public TcpTransport(String host, int port, int connectTimeoutMs) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /**
     * 从 host:port 形式的地址构造
     *
     * @param address   地址，形如 192.168.1.10:502
     * @param timeoutMs 连接超时（毫秒）
     * @return 传输实例
     */
    public static TcpTransport parse(String address, int timeoutMs) {
        int idx = address.lastIndexOf(':');
        if (idx <= 0 || idx == address.length() - 1) {
            throw new IllegalArgumentException("TCP 地址格式应为 host:port，实际为：" + address);
        }
        String host = address.substring(0, idx);
        int port = Integer.parseInt(address.substring(idx + 1).trim());
        return new TcpTransport(host, port, timeoutMs);
    }

    @Override
    public void open() throws IOException {
        this.close();
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        s.setTcpNoDelay(true);
        this.socket = s;
        this.in = s.getInputStream();
        this.out = s.getOutputStream();
    }

    @Override
    public boolean isOpen() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (!this.isOpen()) {
            throw new IOException("TCP 通道未打开：" + this.description());
        }
        out.write(data);
        out.flush();
    }

    @Override
    public byte[] readExactly(int expectedLength, int timeoutMs) throws IOException {
        if (!this.isOpen()) {
            throw new IOException("TCP 通道未打开：" + this.description());
        }
        return DeviceTransport.readFully(in, expectedLength, timeoutMs);
    }

    @Override
    public void purge() throws IOException {
        if (in == null) {
            return;
        }
        while (in.available() > 0) {
            in.skip(in.available());
        }
    }

    @Override
    public String description() {
        return "TCP " + host + ":" + port;
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
    }
}
