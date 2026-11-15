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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 进程内 Modbus TCP 从站，用于端到端验证 TCP 传输与 MBAP 组帧。
 *
 * <p>监听回环地址的随机端口，按 MBAP + PDU 规则应答，并记录收到的事务标识。</p>
 */
final class FakeModbusTcpSlave implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final int[] holding;
    private final int[] input;
    private final List<Integer> transactionIds = Collections.synchronizedList(new ArrayList<>());
    private final Thread thread;

    private volatile boolean running = true;
    private volatile boolean dropFirstConnection;
    private volatile int forcedExceptionCode = -1;
    private int connectionCount;

    FakeModbusTcpSlave(int[] holding, int[] input) throws IOException {
        this.holding = holding.clone();
        this.input = input.clone();
        this.serverSocket = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
        this.thread = new Thread(this::serve, "fake-modbus-tcp-slave");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** 监听端口。 */
    int port() {
        return serverSocket.getLocalPort();
    }

    /** 服务地址，形如 127.0.0.1:502。 */
    String address() {
        return "127.0.0.1:" + this.port();
    }

    /** 收到的连接数。 */
    int connectionCount() {
        synchronized (this) {
            return connectionCount;
        }
    }

    /** 收到的事务标识序列。 */
    List<Integer> transactionIds() {
        synchronized (transactionIds) {
            return new ArrayList<>(transactionIds);
        }
    }

    /** 令首个连接在应答前被直接关闭，用于验证重连重试。 */
    void dropFirstConnection() {
        this.dropFirstConnection = true;
    }

    /** 令从站固定返回指定异常码。 */
    void forceException(int code) {
        this.forcedExceptionCode = code;
    }

    @Override
    public void close() throws IOException {
        running = false;
        serverSocket.close();
        thread.interrupt();
    }

    /** 接受连接并逐个服务。 */
    private void serve() {
        while (running) {
            try (Socket socket = serverSocket.accept()) {
                synchronized (this) {
                    connectionCount++;
                }
                if (dropFirstConnection && connectionCount == 1) {
                    dropFirstConnection = false;
                    continue;
                }
                this.handle(socket);
            } catch (IOException e) {
                if (running && !serverSocket.isClosed()) {
                    // 单个连接异常不影响继续服务
                    continue;
                }
                return;
            }
        }
    }

    /** 处理单个连接的请求/应答。 */
    private void handle(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        while (running) {
            byte[] header = new byte[7];
            if (!this.readFully(in, header)) {
                return;
            }
            int tid = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
            int unit = header[6] & 0xFF;
            int pduLength = (((header[4] & 0xFF) << 8) | (header[5] & 0xFF)) - 1;
            if (pduLength <= 0) {
                return;
            }
            byte[] pdu = new byte[pduLength];
            if (!this.readFully(in, pdu)) {
                return;
            }
            transactionIds.add(tid);
            byte[] responsePdu = this.respond(pdu);
            if (responsePdu == null) {
                return;
            }
            byte[] response = new byte[7 + responsePdu.length];
            response[0] = (byte) ((tid >>> 8) & 0xFF);
            response[1] = (byte) (tid & 0xFF);
            response[2] = 0;
            response[3] = 0;
            int length = 1 + responsePdu.length;
            response[4] = (byte) ((length >>> 8) & 0xFF);
            response[5] = (byte) (length & 0xFF);
            response[6] = (byte) unit;
            System.arraycopy(responsePdu, 0, response, 7, responsePdu.length);
            out.write(response);
            out.flush();
        }
    }

    /** 读取恰好 buffer.length 字节，连接结束返回 false。 */
    private boolean readFully(InputStream in, byte[] buffer) throws IOException {
        int read = 0;
        while (read < buffer.length) {
            int n;
            try {
                n = in.read(buffer, read, buffer.length - read);
            } catch (EOFException e) {
                return false;
            }
            if (n < 0) {
                return false;
            }
            read += n;
        }
        return true;
    }

    /** 按 PDU 生成应答 PDU。 */
    private byte[] respond(byte[] pdu) {
        int function = pdu[0] & 0xFF;
        if (forcedExceptionCode > 0) {
            return new byte[] {(byte) (function | 0x80), (byte) forcedExceptionCode};
        }
        if (function == 0x03 || function == 0x04) {
            int start = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
            int count = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
            int[] table = function == 0x03 ? holding : input;
            if (count < 1 || start < 0 || start + count > table.length) {
                return new byte[] {(byte) (function | 0x80), 2};
            }
            byte[] response = new byte[2 + count * 2];
            response[0] = (byte) function;
            response[1] = (byte) (count * 2);
            for (int i = 0; i < count; i++) {
                response[2 + i * 2] = (byte) ((table[start + i] >>> 8) & 0xFF);
                response[3 + i * 2] = (byte) (table[start + i] & 0xFF);
            }
            return response;
        }
        return new byte[] {(byte) (function | 0x80), 1};
    }
}
