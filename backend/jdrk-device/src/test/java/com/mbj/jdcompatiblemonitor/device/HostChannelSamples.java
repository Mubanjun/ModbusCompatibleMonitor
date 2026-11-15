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

import com.mbj.jdcompatiblemonitor.protocol.ValueEncoding;

/**
 * 现场 1-8 通道的真实配置与实测原始值。
 *
 * <p>配置来自主机配置软件「通道参数」页截图，原始值来自通过映射虚拟端口
 * COM1 以 RS-Modbus 115200 轮询得到的读数，用作回归基准。</p>
 */
public final class HostChannelSamples {

    private HostChannelSamples() {
    }

    /** 1 号通道：电导率，模拟量2，原始值即 µS/cm。 */
    public static ChannelConfig channel1Conductivity() {
        return new ChannelConfig(1, ChannelDataType.ANALOG2_ONLY,
            AnalogDefinition.disabled(),
            new AnalogDefinition(ElementType.CONDUCTIVITY, "电导率", "us/cm",
                ValueEncoding.UINT16_SCALE_1, 1.0D, 0.0D, 20000.0D, 0.0D));
    }

    /** 2 号通道：温度与 PH，两个模拟量都启用。 */
    public static ChannelConfig channel2PhAndTemperature() {
        return new ChannelConfig(2, ChannelDataType.BOTH,
            new AnalogDefinition(ElementType.TEMPERATURE, "温度", "℃",
                ValueEncoding.INT16_SCALE_10, 0.1D, 0.0D, 100.0D, 0.0D),
            new AnalogDefinition(ElementType.PH, "PH", "",
                ValueEncoding.UINT16_SCALE_100, 0.01D, 0.0D, 14.0D, 0.0D));
    }

    /** 3 号通道：溶解氧，32 位大端浮点。 */
    public static ChannelConfig channel3DissolvedOxygen() {
        return new ChannelConfig(3, ChannelDataType.FLOAT32,
            new AnalogDefinition(ElementType.DISSOLVED_OXYGEN, "溶解氧", "mg/L",
                ValueEncoding.FLOAT32_BIG_ENDIAN, 1.0D, 0.0D, 20.0D, 0.0D),
            AnalogDefinition.disabled());
    }

    /** 4 号通道：蓝绿藻，32 位大端浮点。 */
    public static ChannelConfig channel4BlueGreenAlgae() {
        return new ChannelConfig(4, ChannelDataType.FLOAT32,
            // 配置软件界面把单位显示为截断的 cells/，经确认实际为 cells/ml
            new AnalogDefinition(ElementType.BLUE_GREEN_ALGAE, "蓝绿藻", "cells/ml",
                ValueEncoding.FLOAT32_BIG_ENDIAN, 1.0D, 0.0D, 300000.0D, 0.0D),
            AnalogDefinition.disabled());
    }

    /** 5 号通道：浊度，模拟量2，缩放 0.1。 */
    public static ChannelConfig channel5Turbidity() {
        return new ChannelConfig(5, ChannelDataType.ANALOG2_ONLY,
            AnalogDefinition.disabled(),
            new AnalogDefinition(ElementType.TURBIDITY, "浊度", "NTU",
                ValueEncoding.UINT16_SCALE_10, 0.1D, 0.0D, 4000.0D, 0.0D));
    }

    /** 6 号通道：氨氮，模拟量2，缩放 0.01。 */
    public static ChannelConfig channel6Ammonia() {
        return new ChannelConfig(6, ChannelDataType.ANALOG2_ONLY,
            AnalogDefinition.disabled(),
            new AnalogDefinition(ElementType.AMMONIA_NITROGEN, "氨氮", "mg/L",
                ValueEncoding.UINT16_SCALE_100, 0.01D, 0.0D, 100.0D, 0.0D));
    }

    /** 7 号通道：COD，模拟量2，缩放 0.1。 */
    public static ChannelConfig channel7Cod() {
        return new ChannelConfig(7, ChannelDataType.ANALOG2_ONLY,
            AnalogDefinition.disabled(),
            new AnalogDefinition(ElementType.COD, "COD", "mg/L",
                ValueEncoding.UINT16_SCALE_10, 0.1D, 0.0D, 500.0D, 0.0D));
    }

    /** 8 号通道：叶绿素，模拟量2，缩放 0.1。 */
    public static ChannelConfig channel8Chlorophyll() {
        return new ChannelConfig(8, ChannelDataType.ANALOG2_ONLY,
            AnalogDefinition.disabled(),
            new AnalogDefinition(ElementType.CHLOROPHYLL, "叶绿素", "ug/L",
                ValueEncoding.UINT16_SCALE_10, 0.1D, 0.0D, 400.0D, 0.0D));
    }
}
