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
 * 某要素的一个量程档位。
 *
 * <p>同一要素在不同量程下缩放不同，例如浊度 50NTU 档为 ÷100、200/1000/4000NTU 档为 ÷10；
 * 电导率 1~20 为 ÷1000、10~20000 为实际值。因此量程必须显式建模。</p>
 *
 * @param model      型号或档位标识
 * @param label      档位说明
 * @param min        量程下限（工程值）
 * @param max        量程上限（工程值）
 * @param encoding   寄存器编码方式
 * @param resolution 分辨率（工程值，0 表示未定义）
 */
public record ElementRange(String model, String label, double min, double max,
    ValueEncoding encoding, double resolution) {

    public boolean contains(double engineeringValue) {
        return engineeringValue >= min && engineeringValue <= max;
    }

    /** 主机通道系数 A（= 1 ÷ 倍数）。 */
    public double hostCoefficientA() {
        return encoding.hostCoefficientA();
    }
}
