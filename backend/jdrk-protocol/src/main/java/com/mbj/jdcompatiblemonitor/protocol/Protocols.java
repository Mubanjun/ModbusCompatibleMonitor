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
import com.mbj.jdcompatiblemonitor.protocol.transport.Rfc2217Transport;
import com.mbj.jdcompatiblemonitor.protocol.transport.SerialTransport;
import com.mbj.jdcompatiblemonitor.protocol.transport.TcpTransport;

/**
 * 协议工厂：按端口配置与协议名构造协议实例。
 *
 * <p>FR-PROTO-05 要求「新增一种协议仅新增实现与配置」，本工厂是唯一的装配点。</p>
 */
public final class Protocols {

    /** 标准 Modbus RTU */
    public static final String MODBUS_RTU = "MODBUS_RTU";
    /** 仁科 RS-Modbus（帧格式同 RTU，寄存器语义不同） */
    public static final String RS_MODBUS = "RS_MODBUS";
    /** Modbus TCP */
    public static final String MODBUS_TCP = "MODBUS_TCP";
    /** Modbus ASCII（FR-PROTO-04，可选协议） */
    public static final String MODBUS_ASCII = "MODBUS_ASCII";

    /** 串口链路 */
    public static final String PORT_TYPE_SERIAL = "SERIAL";
    /** TCP 链路 */
    public static final String PORT_TYPE_TCP = "TCP";
    /** RFC2217 串口服务器链路 */
    public static final String PORT_TYPE_RFC2217 = "RFC2217";

    private Protocols() {
    }

    /**
     * 构造协议实例
     *
     * @param protocol    协议名
     * @param portType    链路类型（SERIAL / TCP）
     * @param portName    串口号或 host:port
     * @param baudRate    波特率（TCP 忽略）
     * @param dataBits    数据位
     * @param stopBits    停止位
     * @param parity      校验位
     * @param flowControl 流控
     * @param timeoutMs   单帧超时
     * @param retries     失败重试次数
     * @return 协议实例
     */
    public static DeviceProtocol create(String protocol, String portType, String portName,
        int baudRate,
        int dataBits, int stopBits, int parity, int flowControl, int timeoutMs, int retries) {
        DeviceTransport transport;
        if (PORT_TYPE_TCP.equalsIgnoreCase(portType)) {
            transport = TcpTransport.parse(portName, timeoutMs);
        } else if (PORT_TYPE_RFC2217.equalsIgnoreCase(portType)) {
            transport =
                Rfc2217Transport.parse(portName, baudRate, dataBits, stopBits, parity, flowControl,
                    timeoutMs);
        } else {
            transport =
                new SerialTransport(portName, baudRate, dataBits, stopBits, parity, flowControl);
        }
        if (MODBUS_TCP.equalsIgnoreCase(protocol)) {
            return new ModbusTcpProtocol(transport, timeoutMs, retries);
        }
        if (MODBUS_ASCII.equalsIgnoreCase(protocol)) {
            return new ModbusAsciiProtocol(transport, timeoutMs, retries);
        }
        // MODBUS_RTU 与 RS_MODBUS 共用 RTU 实现
        return new ModbusRtuProtocol(transport, timeoutMs, retries);
    }
}
