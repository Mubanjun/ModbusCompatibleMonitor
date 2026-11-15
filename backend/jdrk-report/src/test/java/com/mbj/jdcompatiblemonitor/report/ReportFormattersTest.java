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

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 上报报文格式化的单元测试（FR-RPT-02）。 */
class ReportFormattersTest {

    private static final long COLLECT_TIME = 1_790_000_000_000L;

    private static ReportBatch batch() {
        return new ReportBatch(1L, "5 分钟上报", 2L, "厂区 MQTT", "COM1", COLLECT_TIME, List.of(
            new ReportItem(1, "1 号站", 1, 2, "PH", "pH", "pH", 7.42D, 742.0D, COLLECT_TIME, false,
                117.12D, 36.65D),
            new ReportItem(1, "1 号站", 1, 1, "TEMP", "温度", "℃", 19.9D, 199.0D, COLLECT_TIME, true,
                null, null)));
    }

    @Test
    void formatsJsonBatch() {
        String payload = new JsonReportFormatter().format(batch(), TransformSpec.empty(), null);
        JSONObject json = JSONUtil.parseObj(payload);
        assertEquals(1L, json.getLong("ruleId"));
        assertEquals("厂区 MQTT", json.getStr("targetName"));
        assertEquals(2, json.getInt("count"));
        JSONArray samples = json.getJSONArray("samples");
        assertEquals(2, samples.size());
        assertEquals(7.42D, samples.getJSONObject(0).getDouble("value"), 1e-9);
        assertEquals(1, samples.getJSONObject(1).getInt("alarm"));
        assertTrue(json.containsKey("batchTime"));
        assertTrue(json.containsKey("batchTimestamp"), "保留毫秒时间戳以便死信重放");
        assertEquals(ReportVariables.SCHEMA_VERSION, json.getInt("schemaVersion"),
            "对外报文必须带格式版本，集成方据此判断字段契约（NFR-MAINT-07）");
        assertEquals(COLLECT_TIME, samples.getJSONObject(0).getLong("collectTimestamp"));
    }

    @Test
    void formatsJsonWithTransform() {
        TransformSpec spec = new TransformSpec(Map.of("deviceName", "point"),
            Map.of("value", new UnitConvert(0.001D, 0.0D)), java.util.Set.of(), java.util.Set.of(),
            java.util.Set.of("rawValue", "alarm"));
        JSONObject json = JSONUtil.parseObj(new JsonReportFormatter().format(batch(), spec, null));
        JSONObject first = json.getJSONArray("samples").getJSONObject(0);
        assertEquals("1 号站", first.getStr("point"));
        assertEquals(0.00742D, first.getDouble("value"), 1e-12);
        assertFalse(first.containsKey("rawValue"));
    }

    @Test
    void formatsKvBatch() {
        String payload = new KvReportFormatter().format(batch(), TransformSpec.empty(), null);
        String[] lines = payload.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("ruleName=5 分钟上报"));
        assertTrue(lines[0].contains("deviceName=1 号站"));
        assertTrue(lines[0].contains("value=7.42"));
        assertTrue(lines[1].contains("value=19.9"));
        assertTrue(lines[0].contains("schemaVersion=" + ReportVariables.SCHEMA_VERSION),
            "KV 报文同样带格式版本");
    }

    @Test
    void formatsCustomTemplatePerItem() {
        String template = "{\"n\":\"${deviceName}\",\"v\":${value}}";
        String payload =
            new TemplateReportFormatter().format(batch(), TransformSpec.empty(), template);
        assertEquals("{\"n\":\"1 号站\",\"v\":7.42}\n{\"n\":\"1 号站\",\"v\":19.9}", payload);
    }

    @Test
    void formatsCustomTemplateWithSamplesJson() {
        String template = "{\"rule\":\"${ruleName}\",\"count\":${count},\"data\":${samplesJson}}";
        String payload =
            new TemplateReportFormatter().format(batch(), TransformSpec.empty(), template);
        assertTrue(payload.contains("\"count\":2"));
        assertTrue(payload.contains("\"data\":["));
        assertEquals(1, payload.split("\n").length, "整批只渲染一次");
    }

    @Test
    void registryFallsBackToJson() {
        assertEquals(ReportFormat.JSON, ReportFormatters.of("json").format());
        assertEquals(ReportFormat.CUSTOM, ReportFormatters.of("CUSTOM").format());
        assertEquals(ReportFormat.JSON, ReportFormatters.of("unknown").format());
        assertEquals(ReportFormat.KV, ReportFormatters.of(ReportFormat.parse("kv")).format());
    }

    @Test
    void formatsEmptyBatch() {
        ReportBatch empty = new ReportBatch(1L, "空批次", 2L, "厂区 MQTT", "COM1", COLLECT_TIME,
            List.of());
        JSONObject json = JSONUtil.parseObj(new JsonReportFormatter().format(empty,
            TransformSpec.empty(), null));
        assertEquals(0, json.getJSONArray("samples").size());
        assertEquals("", new KvReportFormatter().format(empty, TransformSpec.empty(), null));
        assertEquals("", new TemplateReportFormatter().format(empty, TransformSpec.empty(), "x"));
    }
}
