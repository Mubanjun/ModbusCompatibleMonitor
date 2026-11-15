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

import com.mbj.jdcompatiblemonitor.device.ChannelConfig;

/**
 * 一个主机通道的采集绑定：寄存器地址 + 换算配置。
 *
 * @param channelNo      通道号（RS-Modbus 下即从站地址）
 * @param regAddr        寄存器起始地址
 * @param registerCount  需要读取的寄存器个数
 * @param config         通道换算配置
 * @author jdrk
 * @since 2026/9/23
 */
public record ChannelBinding(int channelNo, int regAddr, int registerCount, ChannelConfig config) {
}
