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

import com.mbj.jdcompatiblemonitor.protocol.RsModbusCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * 按主机通道配置把寄存器原始值换算成工程量。
 *
 * <p>关键规则（已由现场配置验证）：</p>
 * <ul>
 *   <li>寄存器 0x0000 = 模拟量2，0x0001 = 模拟量1；</li>
 *   <li>被禁用的模拟量其寄存器是无效值，必须丢弃，不能入库；</li>
 *   <li>工程值 = 原始值 × A + B，主机不会替我们乘系数；</li>
 *   <li>浮点型数据由 0x0000 与 0x0001 组成 32 位大端浮点，对应模拟量1 的名称与单位。</li>
 * </ul>
 */
public final class ChannelValueReader {

    private ChannelValueReader() {
    }

    /**
     * 逐模拟量产出原始值与工程量（采集入库使用）
     *
     * @param config    通道配置
     * @param registers 从 {@code regAddr} 起读到的寄存器
     * @return 启用的模拟量样本，按模拟量序号升序
     */
    public static List<AnalogSample> readSamples(ChannelConfig config, int[] registers) {
        List<AnalogSample> samples = new ArrayList<>(2);
        switch (config.dataType()) {
            case FLOAT32 -> {
                requireRegisters(registers, 2);
                requireConfigured(config.channelNo(), "模拟量1（浮点值的名称/单位/系数取自模拟量1）",
                    config.analog1());
                samples.add(sample(config.channelNo(), 1, config.analog1(), registers, 0));
            }
            case ANALOG2_ONLY -> {
                requireRegisters(registers, 1);
                requireConfigured(config.channelNo(), "模拟量2", config.analog2());
                samples.add(sample(config.channelNo(), 2, config.analog2(), registers,
                    RsModbusCodec.REGISTER_ANALOG2));
            }
            case ANALOG1_ONLY -> {
                requireRegisters(registers, 2);
                requireConfigured(config.channelNo(), "模拟量1", config.analog1());
                samples.add(sample(config.channelNo(), 1, config.analog1(), registers,
                    RsModbusCodec.REGISTER_ANALOG1));
            }
            case BOTH -> {
                requireRegisters(registers, 2);
                // 数据属性是「模拟量1 启用 / 模拟量2 启用」，但某一侧可能没有对应的通道行
                // （例如只建了一行模拟量2）。此时该侧在主机里仍是禁用状态，其寄存器是无效值，
                // 必须丢弃——否则会写出一条没有名称、系数取默认 0.1 的垃圾实时值。
                if (config.analog2().isConfigured()) {
                    samples.add(sample(config.channelNo(), 2, config.analog2(), registers,
                        RsModbusCodec.REGISTER_ANALOG2));
                }
                if (config.analog1().isConfigured()) {
                    samples.add(sample(config.channelNo(), 1, config.analog1(), registers,
                        RsModbusCodec.REGISTER_ANALOG1));
                }
                if (samples.isEmpty()) {
                    throw new IllegalStateException("通道 " + config.channelNo()
                        + " 数据属性为 BOTH，但模拟量1/2 都没有配置（请至少补齐一行通道参数）");
                }
            }
            default -> throw new IllegalStateException("未知数据属性：" + config.dataType());
        }
        return samples;
    }

    /**
     * 按通道配置把寄存器原始值换算为工程量，被禁用的模拟量不产出数值
     *
     * @param config    通道配置
     * @param registers 从 {@code regAddr} 起读到的寄存器
     * @return 通道读数
     */
    public static ChannelReading read(ChannelConfig config, int[] registers) {
        Double analog1Value = null;
        Double analog2Value = null;
        for (AnalogSample sample : readSamples(config, registers)) {
            if (sample.analogNo() == 1) {
                analog1Value = sample.engineeringValue();
            } else {
                analog2Value = sample.engineeringValue();
            }
        }
        String unit1 = config.analog1().effectiveUnit();
        String unit2 = config.analog2().effectiveUnit();
        // 只启用一个模拟量时，该模拟量的单位同时作为两个展示位（与主机显示一致）
        switch (config.dataType()) {
            case ANALOG2_ONLY -> unit1 = unit2;
            case ANALOG1_ONLY -> unit2 = unit1;
            default -> {
                // 两个模拟量都在用，各自保留自己的单位
            }
        }
        return new ChannelReading(config.channelNo(), analog1Value, analog2Value, unit1, unit2);
    }

    /** 构造单个模拟量样本。 */
    private static AnalogSample sample(int channelNo, int analogNo, AnalogDefinition analog,
        int[] registers, int offset) {
        double raw = analog.encoding().decodeRaw(registers, offset);
        return new AnalogSample(channelNo, analogNo, raw, analog.toEngineering(raw),
            analog.effectiveUnit());
    }

    private static void requireRegisters(int[] registers, int needed) {
        if (registers == null || registers.length < needed) {
            throw new IllegalArgumentException("need at least " + needed + " register(s)");
        }
    }

    /**
     * 校验某一路模拟量已配置
     *
     * @param channelNo 通道号
     * @param label     模拟量名称（用于错误信息）
     * @param analog    模拟量定义
     */
    private static void requireConfigured(int channelNo, String label, AnalogDefinition analog) {
        if (!analog.isConfigured()) {
            throw new IllegalStateException("通道 " + channelNo + " 缺少 " + label
                + " 的通道配置，请补齐通道参数（禁用状态的寄存器是无效值，不能当作数据入库）");
        }
    }
}
