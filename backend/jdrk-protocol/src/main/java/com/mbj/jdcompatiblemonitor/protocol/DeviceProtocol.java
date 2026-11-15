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

import java.io.Closeable;
import java.util.List;
import java.util.function.Supplier;

/**
 * 设备协议插件（FR-PROTO-05）。
 *
 * <p>采集调度只依赖本接口，新增协议只需新增实现与配置，不改动调度逻辑。
 * 实现需保证：单次调用失败抛 {@link ProtocolException}，不吞异常。</p>
 */
public interface DeviceProtocol extends Closeable {

    /**
     * 协议名称（用于日志与信令）
     *
     * @return 名称
     */
    String name();

    /**
     * 建立或复用连接
     *
     * @throws ProtocolException 连接失败
     */
    void connect() throws ProtocolException;

    /**
     * 连接是否可用
     *
     * @return 是否可用
     */
    boolean isConnected();

    /**
     * 健康检查：执行一次轻量读，确认链路可用
     *
     * @throws ProtocolException 链路不可用
     */
    void checkHealth() throws ProtocolException;

    /**
     * 读保持寄存器（功能码 03）
     *
     * @param slaveAddress  从站地址
     * @param startRegister 起始寄存器
     * @param registerCount 寄存器数量
     * @return 寄存器值
     */
    int[] readHoldingRegisters(int slaveAddress, int startRegister, int registerCount);

    /**
     * 读输入寄存器（功能码 04）
     *
     * @param slaveAddress  从站地址
     * @param startRegister 起始寄存器
     * @param registerCount 寄存器数量
     * @return 寄存器值
     */
    int[] readInputRegisters(int slaveAddress, int startRegister, int registerCount);

    /**
     * 写单个寄存器（功能码 06）
     *
     * @param slaveAddress 从站地址
     * @param register     寄存器地址
     * @param value        写入值
     */
    void writeSingleRegister(int slaveAddress, int register, int value);

    /**
     * 写多个寄存器（功能码 16）
     *
     * @param slaveAddress  从站地址
     * @param startRegister 起始寄存器
     * @param values        写入值
     */
    void writeMultipleRegisters(int slaveAddress, int startRegister, int[] values);

    /**
     * 发送自定义帧并读取应答（FR-PROTO-08 数据透传）
     *
     * @param frame          待发送帧
     * @param expectedLength 期望应答字节数，小于等于 0 表示不读取
     * @return 应答字节，未读取时为空数组
     */
    byte[] transact(byte[] frame, int expectedLength);

    /**
     * 在单次调用范围内覆盖单帧超时（FR-ACQ-03 设备级超时）
     *
     * <p>默认实现忽略覆盖值，直接执行；支持超时覆盖的实现可以据此在
     * 不重建连接的前提下按设备调整超时。</p>
     *
     * @param perCallTimeoutMs 本次调用的超时（毫秒）
     * @param action           待执行动作
     * @param <T>              返回值类型
     * @return 动作返回值
     */
    default <T> T callWithTimeout(int perCallTimeoutMs, Supplier<T> action) {
        return action.get();
    }

    /**
     * 取出并清空累积的报文留痕
     *
     * @return 留痕列表
     */
    List<FrameTrace> drainTraces();

    /**
     * 关闭连接
     */
    @Override
    void close();
}
