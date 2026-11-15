/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
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

package top.continew.admin.monitor.runtime.session;

import com.mbj.jdcompatiblemonitor.protocol.DeviceProtocol;
import com.mbj.jdcompatiblemonitor.protocol.FrameTrace;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.continew.admin.monitor.model.entity.BizPortDO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 端口会话：一条 485 总线（或一个串口服务器连接）对应一个实例。
 *
 * <p>同一条 485 总线上的所有设备共用一次物理连接，必须串行收发，
 * 因此所有读写都经过同一把公平锁（防止慢设备饿死后面的设备）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
public final class PortSession implements AutoCloseable {

    private final BizPortDO port;
    private final DeviceProtocol protocol;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final List<DeviceSession> devices = new ArrayList<>();

    @Getter
    private final AtomicBoolean paused = new AtomicBoolean(false);
    @Getter
    private final AtomicBoolean busy = new AtomicBoolean(false);
    @Getter
    private volatile String lastError;

    public PortSession(BizPortDO port, DeviceProtocol protocol) {
        this.port = port;
        this.protocol = protocol;
    }

    /**
     * 读保持寄存器（功能码 03），按设备级超时执行
     *
     * @param slaveAddress 从站地址
     * @param startRegister 起始寄存器
     * @param registerCount 寄存器数量
     * @param timeoutMs     本次调用超时
     * @return 寄存器值
     */
    public int[] readHoldingRegisters(int slaveAddress, int startRegister, int registerCount,
        int timeoutMs) {
        return this.call(() -> protocol.readHoldingRegisters(slaveAddress, startRegister,
            registerCount), timeoutMs);
    }

    /**
     * 写单个寄存器（功能码 06），按设备级超时执行
     *
     * @param slaveAddress 从站地址
     * @param register     寄存器地址
     * @param value        写入值
     * @param timeoutMs    本次调用超时
     */
    public void writeSingleRegister(int slaveAddress, int register, int value, int timeoutMs) {
        this.run(() -> {
            protocol.writeSingleRegister(slaveAddress, register, value);
            return Boolean.TRUE;
        }, timeoutMs);
    }

    /**
     * 写多个寄存器（功能码 16），按设备级超时执行
     *
     * @param slaveAddress  从站地址
     * @param startRegister 起始寄存器
     * @param values        写入值
     * @param timeoutMs     本次调用超时
     */
    public void writeMultipleRegisters(int slaveAddress, int startRegister, int[] values,
        int timeoutMs) {
        this.run(() -> {
            protocol.writeMultipleRegisters(slaveAddress, startRegister, values);
            return Boolean.TRUE;
        }, timeoutMs);
    }

    /**
     * 发送自定义帧并读取应答（FR-PROTO-08）
     *
     * @param frame          待发送帧
     * @param expectedLength 期望应答长度
     * @return 应答字节
     */
    public byte[] transact(byte[] frame, int expectedLength) {
        lock.lock();
        try {
            return protocol.transact(frame, expectedLength);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取出并清空累积的报文留痕
     *
     * @return 留痕列表
     */
    public List<FrameTrace> drainTraces() {
        return protocol.drainTraces();
    }

    /**
     * 通道是否已连通
     *
     * @return 是否连通
     */
    public boolean isConnected() {
        try {
            return protocol.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 端口配置
     *
     * @return 端口配置
     */
    public BizPortDO port() {
        return port;
    }

    /**
     * 端口下的设备（按采集顺序）
     *
     * @return 只读设备列表
     */
    public List<DeviceSession> devices() {
        return Collections.unmodifiableList(devices);
    }

    /**
     * 设置端口下的设备
     *
     * @param sortedDevices 已排序设备列表
     */
    public void setDevices(List<DeviceSession> sortedDevices) {
        devices.clear();
        devices.addAll(sortedDevices);
    }

    @Override
    public void close() {
        lock.lock();
        try {
            protocol.close();
        } catch (Exception e) {
            log.warn("关闭端口 [{}] 失败：{}", port.getName(), e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 串行执行一次写类协议调用
     *
     * @param action    协议动作
     * @param timeoutMs 本次调用超时（毫秒）
     */
    private void run(Supplier<Boolean> action, int timeoutMs) {
        this.call(action, timeoutMs);
    }

    private <T> T call(Supplier<T> action, int timeoutMs) {
        lock.lock();
        try {
            return protocol.callWithTimeout(timeoutMs, action);
        } catch (RuntimeException e) {
            lastError = e.getMessage();
            throw e;
        } finally {
            lock.unlock();
        }
    }
}
