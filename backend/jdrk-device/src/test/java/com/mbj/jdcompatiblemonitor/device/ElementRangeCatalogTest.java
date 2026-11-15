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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 量程字典与主机系数的一致性验证。 */
class ElementRangeCatalogTest {

    @Test
    void everyElementHasAtLeastOneRange() {
        for (ElementType type : ElementType.values()) {
            assertFalse(ElementRangeCatalog.rangesOf(type).isEmpty(), "missing ranges for " + type);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    @Test
    void conductivityKFoldedIntoEncoding() {
        // 量程 10~20000 的档位为实际值，对应主机系数 A = 1.0
        ElementRange range = ElementRangeCatalog.byUpperLimit(ElementType.CONDUCTIVITY, 20000.0D);
        assertNotNull(range);
        assertEquals(1.0D, range.hostCoefficientA(), 1e-9);
    }

    @Test
    void hostCoefficientMatchesFieldConfiguration() {
        // 现场 1-8 通道的 A 值应与字典推导一致
        assertEquals(1.0D,
            ElementRangeCatalog.byUpperLimit(ElementType.CONDUCTIVITY, 20000.0D).hostCoefficientA(),
            1e-9);
        assertEquals(0.01D,
            ElementRangeCatalog.byUpperLimit(ElementType.PH, 14.0D).hostCoefficientA(), 1e-9);
        assertEquals(0.1D,
            ElementRangeCatalog.byUpperLimit(ElementType.TEMPERATURE, 100.0D).hostCoefficientA(),
            1e-9);
        assertEquals(0.1D,
            ElementRangeCatalog.byUpperLimit(ElementType.TURBIDITY, 4000.0D).hostCoefficientA(),
            1e-9);
        assertEquals(0.01D, ElementRangeCatalog.byUpperLimit(ElementType.AMMONIA_NITROGEN, 100.0D)
            .hostCoefficientA(), 1e-9);
        assertEquals(0.1D,
            ElementRangeCatalog.byUpperLimit(ElementType.COD, 500.0D).hostCoefficientA(), 1e-9);
        assertEquals(0.1D,
            ElementRangeCatalog.byUpperLimit(ElementType.CHLOROPHYLL, 400.0D).hostCoefficientA(),
            1e-9);
    }

    @Test
    void floatsAreModelledAsFloatEncoding() {
        assertEquals(com.mbj.jdcompatiblemonitor.protocol.ValueEncoding.FLOAT32_BIG_ENDIAN,
            ElementRangeCatalog.rangesOf(ElementType.DISSOLVED_OXYGEN).get(0).encoding());
        assertEquals(com.mbj.jdcompatiblemonitor.protocol.ValueEncoding.FLOAT32_BIG_ENDIAN,
            ElementRangeCatalog.rangesOf(ElementType.BLUE_GREEN_ALGAE).get(0).encoding());
        assertEquals(com.mbj.jdcompatiblemonitor.protocol.ValueEncoding.FLOAT32_BIG_ENDIAN,
            ElementRangeCatalog.rangesOf(ElementType.SUSPENDED_SOLIDS).get(0).encoding());
    }
}
