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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modbus 协议公共骨架。
 *
 * <p>承担三件事：把 PDU 交给子类组帧、失败重试、记录收发留痕。
 * 组帧差异（RTU 的从站地址 + CRC16、TCP 的 MBAP）由子类实现。</p>
 */
public abstract class AbstractModbusProtocol implements DeviceProtocol {

    /** 传输通道 */
    protected final DeviceTransport transport;
    /** 单帧超时（毫秒） */
    protected final int timeoutMs;
    /** 失败重试次数（不含首次） */
    protected final int retries;

    private final List<FrameTrace> traces = Collections.synchronizedList(new ArrayList<>());

    /** 单次调用的超时覆盖，仅对当前线程生效（一条 485 总线同一时刻只有一个线程在收发） */
    private final ThreadLocal<Integer> timeoutOverride = new ThreadLocal<>();

    protected AbstractModbusProtocol(DeviceTransport transport, int timeoutMs, int retries) {
        this.transport = transport;
        this.timeoutMs = timeoutMs;
        this.retries = Math.max(0, retries);
    }

    /**
     * 发送 PDU 并返回应答 PDU（含组帧与解帧）
     *
     * @param pdu          请求 PDU
     * @param slaveAddress 从站地址
     * @param functionCode 功能码
     * @return 应答 PDU
     */
    protected abstract byte[] exchange(byte[] pdu, int slaveAddress, int functionCode);

    @Override
    public void connect() {
        try {
            if (!transport.isOpen()) {
                transport.open();
            }
        } catch (Exception e) {
            throw new ProtocolException("打开通道失败：" + transport.description() + "，" + e.getMessage(),
                e);
        }
    }

    @Override
    public boolean isConnected() {
        return transport.isOpen();
    }

    @Override
    public void checkHealth() {
        // 读 1 个保持寄存器作为探活，地址 0 是所有 Modbus 设备都接受的最小地址
        this.readHoldingRegisters(1, 0, 1);
    }

    @Override
    public int[] readHoldingRegisters(int slaveAddress, int startRegister, int registerCount) {
        return this.read(ModbusPdu.FUNCTION_READ_HOLDING, slaveAddress, startRegister,
            registerCount);
    }

    @Override
    public int[] readInputRegisters(int slaveAddress, int startRegister, int registerCount) {
        return this.read(ModbusPdu.FUNCTION_READ_INPUT, slaveAddress, startRegister, registerCount);
    }

    @Override
    public void writeSingleRegister(int slaveAddress, int register, int value) {
        byte[] pdu = ModbusPdu.writeSingleRequest(register, value);
        byte[] response = this.withRetry(pdu, slaveAddress, ModbusPdu.FUNCTION_WRITE_SINGLE);
        ModbusPdu.checkException(response, ModbusPdu.FUNCTION_WRITE_SINGLE);
    }

    @Override
    public void writeMultipleRegisters(int slaveAddress, int startRegister, int[] values) {
        byte[] pdu = ModbusPdu.writeMultipleRequest(startRegister, values);
        byte[] response = this.withRetry(pdu, slaveAddress, ModbusPdu.FUNCTION_WRITE_MULTIPLE);
        ModbusPdu.checkException(response, ModbusPdu.FUNCTION_WRITE_MULTIPLE);
    }

    @Override
    public <T> T callWithTimeout(int perCallTimeoutMs, java.util.function.Supplier<T> action) {
        timeoutOverride.set(perCallTimeoutMs);
        try {
            return action.get();
        } finally {
            timeoutOverride.remove();
        }
    }

    /**
     * 本次调用生效的单帧超时
     *
     * @return 超时（毫秒）
     */
    protected int currentTimeoutMs() {
        Integer override = timeoutOverride.get();
        return override == null ? timeoutMs : override;
    }

    @Override
    public byte[] transact(byte[] frame, int expectedLength) {
        this.connect();
        try {
            transport.purge();
            transport.write(frame);
            traces.add(FrameTrace.out(-1, frame));
            if (expectedLength <= 0) {
                return new byte[0];
            }
            byte[] response = transport.readExactly(expectedLength, this.currentTimeoutMs());
            traces.add(FrameTrace.in(-1, response));
            return response;
        } catch (Exception e) {
            traces.add(FrameTrace.error(-1, e.getMessage()));
            throw new ProtocolException("透传失败：" + transport.description() + "，" + e.getMessage(),
                e);
        }
    }

    @Override
    public List<FrameTrace> drainTraces() {
        synchronized (traces) {
            List<FrameTrace> copy = new ArrayList<>(traces);
            traces.clear();
            return copy;
        }
    }

    @Override
    public void close() {
        transport.close();
    }

    /**
     * 读寄存器
     *
     * @param functionCode  功能码
     * @param slaveAddress  从站地址
     * @param startRegister 起始寄存器
     * @param registerCount 寄存器数量
     * @return 寄存器值
     */
    private int[] read(int functionCode, int slaveAddress, int startRegister, int registerCount) {
        byte[] pdu = ModbusPdu.readRequest(functionCode, startRegister, registerCount);
        this.connect();
        ProtocolException last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            byte[] response;
            try {
                response = this.exchange(pdu, slaveAddress, functionCode);
            } catch (ProtocolException e) {
                last = this.afterFailure(slaveAddress, attempt, e);
                continue;
            }
            // 设备明确返回异常码时重试没有意义，直接上报
            ModbusPdu.checkException(response, functionCode);
            try {
                return ModbusPdu.parseReadResponse(response, functionCode, registerCount);
            } catch (ProtocolException e) {
                // 读到的应答字节数与请求不符（常见于上一帧应答迟到）：按坏帧重试，
                // 绝不把错位数据当成通道值返回
                last = this.afterFailure(slaveAddress, attempt, e);
            }
        }
        String reason = last == null ? "未知原因" : last.getMessage();
        throw new ProtocolException("重试 " + retries + " 次后仍失败：" + reason, last);
    }

    /**
     * 一次失败后的收尾：记录留痕并重开通道，返回供重试循环记录的异常
     *
     * @param slaveAddress 从站地址
     * @param attempt      当前尝试序号（0 起）
     * @param error        本次失败原因
     * @return 原异常
     */
    private ProtocolException afterFailure(int slaveAddress, int attempt, ProtocolException error) {
        traces.add(FrameTrace.error(slaveAddress, "第 " + (attempt + 1) + " 次尝试失败：" + error
            .getMessage()));
        // 失败后重开通道，避免半包残留影响下一次
        transport.close();
        try {
            transport.open();
        } catch (Exception reopenError) {
            throw new ProtocolException("重连失败：" + transport.description() + "，" + reopenError
                .getMessage(), reopenError);
        }
        return error;
    }

    /**
     * 带重试的收发
     *
     * @param pdu          请求 PDU
     * @param slaveAddress 从站地址
     * @param functionCode 功能码
     * @return 应答 PDU
     */
    private byte[] withRetry(byte[] pdu, int slaveAddress, int functionCode) {
        this.connect();
        ProtocolException last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return this.exchange(pdu, slaveAddress, functionCode);
            } catch (ProtocolException e) {
                last = this.afterFailure(slaveAddress, attempt, e);
            }
        }
        throw new ProtocolException("重试 " + retries + " 次后仍失败：" + last.getMessage(), last);
    }

    /**
     * 记录发送留痕
     *
     * @param slaveAddress 从站地址
     * @param frame        报文
     */
    protected void traceOut(int slaveAddress, byte[] frame) {
        traces.add(FrameTrace.out(slaveAddress, frame));
    }

    /**
     * 记录接收留痕
     *
     * @param slaveAddress 从站地址
     * @param frame        报文
     */
    protected void traceIn(int slaveAddress, byte[] frame) {
        traces.add(FrameTrace.in(slaveAddress, frame));
    }

    /**
     * 记录异常留痕
     *
     * @param slaveAddress 从站地址
     * @param note         原因
     */
    protected void traceError(int slaveAddress, String note) {
        traces.add(FrameTrace.error(slaveAddress, note));
    }
}
