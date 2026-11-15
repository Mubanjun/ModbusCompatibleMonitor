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

package com.mbj.jdcompatiblemonitor.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 字段映射、单位换算、脱敏与裁剪的单元测试（FR-RPT-06）。 */
class FieldTransformerTest {

    private static Map<String, Object> source() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("deviceName", "1 号站");
        source.put("nodeId", 1);
        source.put("value", 742.0D);
        source.put("rawValue", 742.0D);
        return source;
    }

    @Test
    void emptySpecKeepsEverything() {
        Map<String, Object> result = FieldTransformer.apply(source(), TransformSpec.empty());
        assertEquals(source(), result);
    }

    @Test
    void appliesMappingUnitsMaskAndSlices() {
        TransformSpec spec = new TransformSpec(Map.of("deviceName", "pointName"),
            Map.of("value", new UnitConvert(0.01D, 0.0D)), java.util.Set.of("deviceName"),
            java.util.Set.of(), java.util.Set.of("rawValue"));
        Map<String, Object> result = FieldTransformer.apply(source(), spec);
        assertEquals(Map.of("pointName", TransformSpec.MASKED, "nodeId", 1, "value", 7.42D),
            result);
        assertFalse(result.containsKey("rawValue"), "黑名单字段被裁剪");
    }

    @Test
    void includeWhitelistKeepsOnlyListedFields() {
        TransformSpec spec = new TransformSpec(Map.of(), Map.of(), java.util.Set.of(),
            java.util.Set.of("deviceName"), java.util.Set.of());
        Map<String, Object> result = FieldTransformer.apply(source(), spec);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("deviceName"));
    }

    @Test
    void unitConversionSupportsOffset() {
        TransformSpec spec =
            new TransformSpec(Map.of(), Map.of("value", new UnitConvert(1.0D, -32.0D)),
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of());
        assertEquals(710.0D, FieldTransformer.apply(source(), spec).get("value"));
    }

    @Test
    void parsesSpecFromConfigJson() {
        String config = "{\"host\":\"127.0.0.1\",\"mapping\":{\"deviceName\":\"pointName\"},"
            + "\"units\":{\"value\":{\"factor\":0.001,\"offset\":0}},"
            + "\"mask\":[\"deviceName\"],\"include\":[\"deviceName\",\"value\"],"
            + "\"exclude\":[\"rawValue\"]}";
        TransformSpec spec = TransformSpec.parse(config);
        assertFalse(spec.isEmpty());
        assertEquals("pointName", spec.mapping().get("deviceName"));
        assertEquals(0.001D, spec.units().get("value").factor(), 1e-9);
        assertTrue(spec.mask().contains("deviceName"));
        assertTrue(spec.include().contains("value"));
        assertTrue(spec.exclude().contains("rawValue"));
    }

    @Test
    void toleratesBrokenConfig() {
        assertTrue(TransformSpec.parse(null).isEmpty());
        assertTrue(TransformSpec.parse("not json").isEmpty());
        assertTrue(TransformSpec.parse("{}").isEmpty());
        assertTrue(TransformSpec.parse("{\"mapping\":null}").isEmpty());
    }
}
