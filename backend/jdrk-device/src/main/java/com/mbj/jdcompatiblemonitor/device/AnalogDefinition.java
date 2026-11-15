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
 * 一个模拟量的定义，对应主机通道参数里的一组配置。
 *
 * @param elementType  要素类型（可为 null 表示未命名）
 * @param name         主机里配置的名称
 * @param unit         主机里配置的单位
 * @param encoding     寄存器编码方式
 * @param coefficientA 系数 A（工程值 = 原始值 × A + B）
 * @param coefficientB 系数 B
 * @param upperLimit   量程上限
 * @param lowerLimit   量程下限
 */
public record AnalogDefinition(ElementType elementType, String name, String unit,
    ValueEncoding encoding, double coefficientA, double coefficientB,
    double upperLimit, double lowerLimit) {

    public static AnalogDefinition disabled() {
        return new AnalogDefinition(null, "", "", ValueEncoding.UINT16_SCALE_10, 0.1D, 0.0D, 0.0D,
            0.0D);
    }

    /** 按要素量程档位生成定义（A 取 1 ÷ 倍数）。 */
    public static AnalogDefinition of(ElementType type, ElementRange range, double upperLimit,
        double lowerLimit) {
        return new AnalogDefinition(type, type.displayName(), type.unit(),
            range.encoding(), range.hostCoefficientA(), 0.0D, upperLimit, lowerLimit);
    }

    /**
     * 有效单位：优先用主机里配置的单位，为空时回退到要素字典的单位。
     *
     * <p>现场 PH 通道在主机里的单位就是空的，但要素类型已知，应能自动补齐。</p>
     */
    public String effectiveUnit() {
        if (unit != null && !unit.isBlank()) {
            return unit;
        }
        return elementType == null ? "" : elementType.unit();
    }

    /**
     * 是否已配置（主机侧启用）。
     *
     * <p>厂家配置软件对「禁用」的模拟量会给默认 A=0.1、名称与单位为空，
     * 对应 {@link #disabled()}；被禁用模拟量的寄存器是无效值，必须丢弃不入库。</p>
     *
     * @return 已配置返回 true
     */
    public boolean isConfigured() {
        return elementType != null || (name != null && !name.isBlank());
    }

    /** 工程值换算。 */
    public double toEngineering(double rawValue) {
        return rawValue * coefficientA + coefficientB;
    }
}
