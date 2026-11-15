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
import java.util.List;
import java.util.Map;

/**
 * 要素量程字典。
 *
 * <p>数据来源：建大仁科官网 26 份 485 型变送器说明书的寄存器章节。
 * 这里是本项目的核心资产之一，设备模板直接引用它，避免为每个型号写死代码。</p>
 */
public final class ElementRangeCatalog {

    private static final Map<ElementType, List<ElementRange>> RANGES = Map.ofEntries(
        Map.entry(ElementType.TEMPERATURE, List.of(
            new ElementRange("通用", "0~100 ℃ 16位有符号 ÷10", 0.0D, 100.0D, ValueEncoding.INT16_SCALE_10,
                0.1D))),

        Map.entry(ElementType.PH, List.of(
            new ElementRange("RS-PH-N01-2/-3", "0~14 pH ÷100", 0.0D, 14.0D,
                ValueEncoding.UINT16_SCALE_100, 0.01D))),

        Map.entry(ElementType.ORP, List.of(
            new ElementRange("RS-ORP-N01-2/-3", "-1999~1999 mV 实际值", -1999.0D, 1999.0D,
                ValueEncoding.INT16_SCALE_1, 1.0D))),

        Map.entry(ElementType.CONDUCTIVITY, List.of(
            new ElementRange("RS-EC-N01-2/-3", "量程 1~20 ÷1000", 1.0D, 20.0D,
                ValueEncoding.UINT16_SCALE_1000, 0.001D),
            new ElementRange("RS-EC-N01-2/-3", "量程 1~200 ÷100", 1.0D, 200.0D,
                ValueEncoding.UINT16_SCALE_100, 0.01D),
            new ElementRange("RS-EC-N01-2/-3", "量程 1~2000 ÷10", 1.0D, 2000.0D,
                ValueEncoding.UINT16_SCALE_10, 0.1D),
            new ElementRange("RS-EC-N01-2/-3", "量程 10~20000 实际值", 10.0D, 20000.0D,
                ValueEncoding.UINT16_SCALE_1, 1.0D))),

        Map.entry(ElementType.WIDE_RANGE_CONDUCTIVITY, List.of(
            new ElementRange("RS-ECW-N01-2/-3", "量程 1~10000 ÷10", 1.0D, 10000.0D,
                ValueEncoding.UINT32_SCALE_10, 0.1D),
            new ElementRange("RS-ECW-N01-2/-3", "量程 10~100000 实际值", 10.0D, 100000.0D,
                ValueEncoding.UINT32_SCALE_1, 1.0D))),

        Map.entry(ElementType.DISSOLVED_OXYGEN, List.of(
            new ElementRange("RS-LDO-N01-2", "0~20 mg/L（0~200%）32位大端浮点", 0.0D, 20.0D,
                ValueEncoding.FLOAT32_BIG_ENDIAN, 0.01D))),

        Map.entry(ElementType.TURBIDITY, List.of(
            new ElementRange("RS-ZD-N01-1", "50NTU 档 ÷100", 0.0D, 50.0D,
                ValueEncoding.UINT16_SCALE_100, 0.01D),
            new ElementRange("RS-ZD-N01-1", "200NTU 档 ÷10", 0.0D, 200.0D,
                ValueEncoding.UINT16_SCALE_10, 0.1D),
            new ElementRange("RS-ZD-N01-1", "1000NTU 档 ÷10", 0.0D, 1000.0D,
                ValueEncoding.UINT16_SCALE_10, 0.1D),
            new ElementRange("RS-ZD-N01-1", "4000NTU 档 ÷10", 0.0D, 4000.0D,
                ValueEncoding.UINT16_SCALE_10, 0.1D))),

        Map.entry(ElementType.RESIDUAL_CHLORINE, List.of(
            new ElementRange("RS-CL-N01-2/-3", "0-2 mg/L ÷100", 0.0D, 2.0D,
                ValueEncoding.UINT16_SCALE_100, 0.01D),
            new ElementRange("RS-CL-N01-2/-3", "0-10 mg/L ÷100", 0.0D, 10.0D,
                ValueEncoding.UINT16_SCALE_100, 0.01D),
            new ElementRange("RS-CL-N01-2/-3", "0-20 mg/L ÷100", 0.0D, 20.0D,
                ValueEncoding.UINT16_SCALE_100, 0.01D))),

        Map.entry(ElementType.AMMONIA_NITROGEN, List.of(
            new ElementRange("RS-NHN-N01-3", "0-10 mg/L ÷100", 0.0D, 10.0D,
                ValueEncoding.UINT16_SCALE_100, 0.01D),
            new ElementRange("RS-NHN-N01-4", "0-100 mg/L ÷100", 0.0D, 100.0D,
                ValueEncoding.UINT16_SCALE_100, 0.01D),
            new ElementRange("RS-NHN-N01-5", "0-1000 mg/L ÷10", 0.0D, 1000.0D,
                ValueEncoding.UINT16_SCALE_10, 0.1D))),

        Map.entry(ElementType.ION, List.of(
            new ElementRange("RS-Lxx-N01-2/-3", "0-100 mg/L ÷100", 0.0D, 100.0D,
                ValueEncoding.UINT16_SCALE_100, 0.01D),
            new ElementRange("RS-Lxx-N01-2/-3", "0-1000 mg/L ÷10", 0.0D, 1000.0D,
                ValueEncoding.UINT16_SCALE_10, 0.1D),
            new ElementRange("RS-Lxx-N01-2/-3", "0-3500 mg/L ÷10", 0.0D, 3500.0D,
                ValueEncoding.UINT16_SCALE_10, 0.1D))),

        Map.entry(ElementType.COD, List.of(
            new ElementRange("RS-COD-N01-2", "0~500 mg/L ÷10", 0.0D, 500.0D,
                ValueEncoding.UINT16_SCALE_10, 0.1D))),

        Map.entry(ElementType.CHLOROPHYLL, List.of(
            new ElementRange("RS-CH-N01-2", "0~400 ug/L ÷10", 0.0D, 400.0D,
                ValueEncoding.UINT16_SCALE_10, 0.1D))),

        Map.entry(ElementType.BLUE_GREEN_ALGAE, List.of(
            new ElementRange("RS-BA-N01-2", "0~300000 cells/ml 32位大端浮点", 0.0D, 300000.0D,
                ValueEncoding.FLOAT32_BIG_ENDIAN, 0.0D))),

        Map.entry(ElementType.SUSPENDED_SOLIDS, List.of(
            new ElementRange("RS-SS-N01-2/-3", "0~20000 mg/L 32位大端浮点", 0.0D, 20000.0D,
                ValueEncoding.FLOAT32_BIG_ENDIAN, 0.1D))),

        Map.entry(ElementType.OIL_IN_WATER, List.of(
            new ElementRange("RS-OIL-N01-2", "ppm ÷100", 0.0D, 100.0D,
                ValueEncoding.UINT16_SCALE_100, 0.01D))),

        Map.entry(ElementType.COLORITY, List.of(
            new ElementRange("RS-WSD-N01-1", "0~500 Hazen ÷10", 0.0D, 500.0D,
                ValueEncoding.UINT16_SCALE_10, 0.1D))));

    private ElementRangeCatalog() {
    }

    public static List<ElementRange> rangesOf(ElementType type) {
        return RANGES.getOrDefault(type, List.of());
    }

    /** 按量程上限匹配档位。 */
    public static ElementRange byUpperLimit(ElementType type, double upperLimit) {
        for (ElementRange range : rangesOf(type)) {
            if (Math.abs(range.max() - upperLimit) < 1e-6) {
                return range;
            }
        }
        return null;
    }
}
