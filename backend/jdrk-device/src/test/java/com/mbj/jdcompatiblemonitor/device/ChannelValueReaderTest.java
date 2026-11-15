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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mbj.jdcompatiblemonitor.protocol.ValueEncoding;
import org.junit.jupiter.api.Test;

/** 用现场实测原始值验证通道换算。 */
class ChannelValueReaderTest {

    @Test
    void conductivityUsesRawValueWithoutExtraScaling() {
        // 现场：0x0000 = 11457（k=10，量程 10~20000，A=1.0）
        ChannelReading reading = ChannelValueReader.read(
            HostChannelSamples.channel1Conductivity(), new int[] {11457, 56651});
        assertTrue(reading.analog2().isPresent());
        assertEquals(11457.0D, reading.analog2().getAsDouble(), 1e-9);
        // 模拟量1 被禁用，其寄存器是无效值，必须丢弃
        assertFalse(reading.analog1().isPresent());
    }

    @Test
    void phAndTemperatureDecodeWithHostCoefficients() {
        // 现场：0x0000 = 742 -> pH 7.42；0x0001 = 199 -> 19.9 ℃
        ChannelReading reading = ChannelValueReader.read(
            HostChannelSamples.channel2PhAndTemperature(), new int[] {742, 199});
        assertEquals(7.42D, reading.analog2().getAsDouble(), 1e-9);
        assertEquals(19.9D, reading.analog1().getAsDouble(), 1e-9);
        assertEquals("pH", reading.analog2Unit());
        assertEquals("℃", reading.analog1Unit());
    }

    @Test
    void dissolvedOxygenDecodesBigEndianFloat() {
        // 现场：0x4105 0x0C72 -> 8.3155 mg/L
        ChannelReading reading = ChannelValueReader.read(
            HostChannelSamples.channel3DissolvedOxygen(), new int[] {0x4105, 0x0C72});
        // 浮点型通道：0x0000+0x0001 组成一个值，名称与单位取自模拟量1
        assertEquals(8.3155D, reading.analog1().getAsDouble(), 1e-4);
        assertEquals("mg/L", reading.analog1Unit());
        assertFalse(reading.analog2().isPresent());
    }

    @Test
    void ammoniaDecodesToHundredths() {
        // 现场：0x0000 = 9 -> 0.09 mg/L
        ChannelReading reading = ChannelValueReader.read(
            HostChannelSamples.channel6Ammonia(), new int[] {9, 29061});
        assertEquals(0.09D, reading.analog2().getAsDouble(), 1e-9);
    }

    @Test
    void zeroValuesStayZero() {
        assertEquals(0.0D, ChannelValueReader.read(
            HostChannelSamples.channel5Turbidity(), new int[] {0, 35907}).analog2().getAsDouble(),
            1e-9);
        assertEquals(0.0D, ChannelValueReader.read(
            HostChannelSamples.channel7Cod(), new int[] {0, 58754}).analog2().getAsDouble(), 1e-9);
        assertEquals(0.0D, ChannelValueReader.read(
            HostChannelSamples.channel8Chlorophyll(), new int[] {0, 59457}).analog2().getAsDouble(),
            1e-9);
    }

    @Test
    void blueGreenAlgaeFloatChannel() {
        ChannelReading reading = ChannelValueReader.read(
            HostChannelSamples.channel4BlueGreenAlgae(), new int[] {0, 0});
        assertEquals(0.0D, reading.analog1().getAsDouble(), 1e-9);
        assertEquals("cells/ml", reading.analog1Unit());
    }

    @Test
    void primaryValuePrefersAnalog2() {
        assertEquals(7.42D, ChannelValueReader.read(
            HostChannelSamples.channel2PhAndTemperature(), new int[] {742, 199}).primary()
            .getAsDouble(), 1e-9);
    }

    @Test
    void readSamplesKeepsRawAndEngineeringValueApart() {
        // 现场：0x0000 = 742 -> pH 7.42；0x0001 = 199 -> 19.9 ℃
        var samples = ChannelValueReader.readSamples(
            HostChannelSamples.channel2PhAndTemperature(), new int[] {742, 199});
        assertEquals(2, samples.size());
        assertEquals(2, samples.get(0).analogNo());
        assertEquals(742.0D, samples.get(0).rawValue(), 1e-9);
        assertEquals(7.42D, samples.get(0).engineeringValue(), 1e-9);
        assertEquals("pH", samples.get(0).unit());
        assertEquals(1, samples.get(1).analogNo());
        assertEquals(199.0D, samples.get(1).rawValue(), 1e-9);
        assertEquals(19.9D, samples.get(1).engineeringValue(), 1e-9);
        assertEquals("℃", samples.get(1).unit());
        assertTrue(samples.get(0).valid());
    }

    @Test
    void readSamplesSkipsDisabledAnalog() {
        var samples = ChannelValueReader.readSamples(
            HostChannelSamples.channel1Conductivity(), new int[] {11457, 56651});
        assertEquals(1, samples.size());
        assertEquals(2, samples.get(0).analogNo());
        assertEquals(11457.0D, samples.get(0).rawValue(), 1e-9);
    }

    @Test
    void readSamplesKeepsFloatBitsAsRaw() {
        var samples = ChannelValueReader.readSamples(
            HostChannelSamples.channel3DissolvedOxygen(), new int[] {0x4105, 0x0C72});
        assertEquals(1, samples.size());
        assertEquals(1, samples.get(0).analogNo());
        assertEquals(8.3155D, samples.get(0).engineeringValue(), 1e-4);
        assertEquals(8.3155D, samples.get(0).rawValue(), 1e-4);
    }

    @Test
    void readSamplesRejectsTooFewRegisters() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> ChannelValueReader.readSamples(HostChannelSamples.channel2PhAndTemperature(),
                new int[] {742}));
    }

    @Test
    void bothSkipsAnalogWithoutChannelRow() {
        // 现场踩过的坑：数据属性选 BOTH 但只建了模拟量2 那一行通道参数，
        // 模拟量1 仍是禁用默认值（A=0.1、名称单位为空），其寄存器是无效值——必须丢弃，
        // 否则 biz_data_latest 里会多出一条没有名称、质量位 BAD 的垃圾实时值。
        ChannelConfig incomplete = new ChannelConfig(9, ChannelDataType.BOTH,
            AnalogDefinition.disabled(),
            new AnalogDefinition(ElementType.CONDUCTIVITY, "电导率", "us/cm",
                ValueEncoding.UINT16_SCALE_1, 1.0D, 0.0D, 20000.0D, 0.0D));
        var samples = ChannelValueReader.readSamples(incomplete, new int[] {11457, 199});
        assertEquals(1, samples.size());
        assertEquals(2, samples.get(0).analogNo());
        assertEquals(11457.0D, samples.get(0).engineeringValue(), 1e-9);
    }

    @Test
    void bothWithBothRowsKeepsTwoSamples() {
        var samples = ChannelValueReader.readSamples(
            HostChannelSamples.channel2PhAndTemperature(), new int[] {742, 199});
        assertEquals(2, samples.size());
    }

    @Test
    void rejectsChannelWithoutAnyAnalogDefinition() {
        ChannelConfig empty =
            new ChannelConfig(9, ChannelDataType.BOTH, AnalogDefinition.disabled(),
                AnalogDefinition.disabled());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> ChannelValueReader.readSamples(empty, new int[] {1, 2}));
    }

    @Test
    void rejectsFloatChannelWithoutAnalog1() {
        ChannelConfig broken = new ChannelConfig(9, ChannelDataType.FLOAT32,
            AnalogDefinition.disabled(), AnalogDefinition.disabled());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> ChannelValueReader.readSamples(broken, new int[] {0x4105, 0x0C72}));
    }
}
