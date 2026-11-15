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

import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 串口传输（RS-485 半双工）。
 *
 * <p>参数取值与 {@code biz_port} 表一致：校验位 0 无 / 1 奇 / 2 偶，
 * 流控 0 无 / 1 RTS-CTS / 2 XON-XOFF。</p>
 */
public final class SerialTransport implements DeviceTransport {

    /**
     * 帧间静默（毫秒）：RS-485 半双工总线上两次发送之间必须留出的最小间隔。
     *
     * <p>规格要求留出 3.5 个字符时间；9600bps 下约 4ms。实测在 HHD 桥接虚拟串口上
     * 「上一帧读完立刻发下一帧」会丢响应，留出静默后明显改善（FR-PROTO-07）。</p>
     */
    private static volatile int frameGapMillis = 5;

    private final String portName;
    private final int baudRate;
    private final int dataBits;
    private final int stopBits;
    private final int parity;
    private final int flowControl;
    private SerialPort port;
    private InputStream in;
    private OutputStream out;
    private volatile long lastWriteAt;

    public SerialTransport(String portName, int baudRate, int dataBits, int stopBits, int parity,
        int flowControl) {
        this.portName = portName;
        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.parity = parity;
        this.flowControl = flowControl;
    }

    @Override
    public void open() throws IOException {
        this.close();
        SerialPort sp = SerialPort.getCommPort(portName);
        sp.setComPortParameters(baudRate, dataBits, toStopBits(stopBits), parity);
        sp.setFlowControl(toFlowControl(flowControl));
        // 读取采用非阻塞模式，超时由 DeviceTransport.readFully 统一控制
        sp.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
        if (!sp.openPort()) {
            throw new IOException("串口打开失败：" + portName);
        }
        this.port = sp;
        this.in = sp.getInputStream();
        this.out = sp.getOutputStream();
    }

    @Override
    public boolean isOpen() {
        return port != null && port.isOpen();
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (!this.isOpen()) {
            throw new IOException("串口未打开：" + portName);
        }
        int gap = frameGapMillis;
        if (gap > 0) {
            long wait = gap - (System.currentTimeMillis() - lastWriteAt);
            if (wait > 0) {
                DeviceTransport.sleepQuietly(wait);
            }
        }
        // 静默等待结束前可能还有上一帧的"迟到应答"到达：调用方的 purge() 发生在等待之前，清不掉它，
        // 于是这条迟到帧会被当成本次请求的应答（同为固定 2 寄存器读取时，个数校验也发现不了）→ 静默写错数据。
        // 因此在真正发送前再清一次接收缓冲，保证读到的第一帧一定属于本次请求。
        this.purge();
        out.write(data);
        out.flush();
        lastWriteAt = System.currentTimeMillis();
    }

    /**
     * 设置帧间静默
     *
     * @param millis 毫秒（0~1000）
     */
    public static void setFrameGapMillis(int millis) {
        frameGapMillis = Math.max(0, Math.min(millis, 1000));
    }

    /**
     * 当前帧间静默
     *
     * @return 毫秒
     */
    public static int frameGapMillis() {
        return frameGapMillis;
    }

    @Override
    public byte[] readExactly(int expectedLength, int timeoutMs) throws IOException {
        if (!this.isOpen()) {
            throw new IOException("串口未打开：" + portName);
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
        return "串口 " + portName + " @ " + baudRate + " " + dataBits + toParityLabel(parity)
            + stopBits;
    }

    @Override
    public void close() {
        if (port != null) {
            port.closePort();
        }
        port = null;
        in = null;
        out = null;
    }

    /**
     * 停止位映射
     *
     * @param value 1 或 2
     * @return jSerialComm 常量
     */
    private static int toStopBits(int value) {
        return value == 2 ? SerialPort.TWO_STOP_BITS : SerialPort.ONE_STOP_BIT;
    }

    /**
     * 流控映射
     *
     * @param value 0 无 / 1 RTS-CTS / 2 XON-XOFF
     * @return jSerialComm 位掩码
     */
    private static int toFlowControl(int value) {
        if (value == 1) {
            return SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED;
        }
        if (value == 2) {
            return SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED
                | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED;
        }
        return SerialPort.FLOW_CONTROL_DISABLED;
    }

    /**
     * 校验位标签
     *
     * @param value 0 无 / 1 奇 / 2 偶
     * @return 标签
     */
    private static String toParityLabel(int value) {
        if (value == 1) {
            return "O";
        }
        return value == 2 ? "E" : "N";
    }
}
