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

package com.mbj.jdcompatiblemonitor.device;

/**
 * 主机一个通道的配置，对应配置软件「通道参数」页。
 *
 * @param channelNo  通道号（同时是 RS-Modbus 从站地址）
 * @param dataType   数据属性
 * @param analog1    模拟量1（对应寄存器 0x0001）
 * @param analog2    模拟量2（对应寄存器 0x0000）
 */
public record ChannelConfig(int channelNo, ChannelDataType dataType,
    AnalogDefinition analog1, AnalogDefinition analog2) {

    public ChannelConfig {
        if (channelNo < 1 || channelNo > 255) {
            throw new IllegalArgumentException("channel number out of range: " + channelNo);
        }
    }
}
