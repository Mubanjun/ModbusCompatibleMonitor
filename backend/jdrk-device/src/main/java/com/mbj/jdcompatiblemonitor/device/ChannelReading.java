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

import java.util.OptionalDouble;

/** 一个通道一次采集的结果。 */
public record ChannelReading(int channelNo, Double analog1Value, Double analog2Value,
    String analog1Unit, String analog2Unit) {

    public OptionalDouble analog1() {
        return analog1Value == null ? OptionalDouble.empty() : OptionalDouble.of(analog1Value);
    }

    public OptionalDouble analog2() {
        return analog2Value == null ? OptionalDouble.empty() : OptionalDouble.of(analog2Value);
    }

    /** 主值：浮点型与仅启用模拟量2 时取模拟量2，否则取模拟量1。 */
    public OptionalDouble primary() {
        if (analog2Value != null) {
            return OptionalDouble.of(analog2Value);
        }
        return analog1();
    }
}
