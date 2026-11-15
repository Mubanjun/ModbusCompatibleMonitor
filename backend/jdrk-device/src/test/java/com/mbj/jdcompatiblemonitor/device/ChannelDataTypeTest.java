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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 通道数据属性的寄存器需求与主机中文名映射测试。 */
class ChannelDataTypeTest {

    @Test
    void declaresRequiredRegisterCount() {
        assertEquals(1, ChannelDataType.ANALOG2_ONLY.requiredRegisterCount());
        assertEquals(2, ChannelDataType.ANALOG1_ONLY.requiredRegisterCount());
        assertEquals(2, ChannelDataType.BOTH.requiredRegisterCount());
        assertEquals(2, ChannelDataType.FLOAT32.requiredRegisterCount());
    }

    @Test
    void mapsHostChineseLabels() {
        assertEquals(ChannelDataType.BOTH,
            ChannelDataType.fromHostLabel("模拟量1启用模拟量2启用"));
        assertEquals(ChannelDataType.FLOAT32, ChannelDataType.fromHostLabel("浮点型数据"));
        assertThrows(IllegalArgumentException.class,
            () -> ChannelDataType.fromHostLabel("不存在的数据属性"));
    }

    @Test
    void analogSampleValidityFollowsFiniteNumbers() {
        assertTrue(new AnalogSample(1, 1, 742D, 7.42D, "pH").valid());
        assertFalse(new AnalogSample(1, 1, 0D, Double.NaN, "pH").valid());
        assertFalse(new AnalogSample(1, 1, 0D, Double.POSITIVE_INFINITY, "pH").valid());
    }
}
