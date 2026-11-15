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
 * 一个模拟量一次采集的结果：原始值与工程量并存（FR-WEB-08「展示原始值与工程量」）。
 *
 * <p>{@code rawValue} 是寄存器里的原始数值（未乘系数 A/B），
 * {@code engineeringValue} 是「原始值 × A + B」后的工程值。</p>
 *
 * @param channelNo        通道号
 * @param analogNo         模拟量序号（1 或 2）
 * @param rawValue         原始值
 * @param engineeringValue 工程值
 * @param unit             该模拟量的单位
 */
public record AnalogSample(int channelNo, int analogNo, double rawValue, double engineeringValue,
    String unit) {

    /** 值是否落在有效数字范围内（NaN 视为无效）。 */
    public boolean valid() {
        return !Double.isNaN(engineeringValue) && !Double.isInfinite(engineeringValue);
    }
}
