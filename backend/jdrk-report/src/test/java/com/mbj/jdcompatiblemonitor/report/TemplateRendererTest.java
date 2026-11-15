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

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 模板渲染的单元测试（FR-RPT-01 主题模板、FR-RPT-02 自定义模板）。 */
class TemplateRendererTest {

    private static final Map<String, Object> VARS = Map.of("deviceName", "1 号站", "value", 7.42D);

    @Test
    void substitutesVariables() {
        assertEquals("jdrk/1 号站/data",
            TemplateRenderer.render("jdrk/${deviceName}/data", VARS));
        assertEquals("值=7.42", TemplateRenderer.render("值=${value}", VARS));
    }

    @Test
    void missingVariableBecomesEmpty() {
        assertEquals("值=", TemplateRenderer.render("值=${unknown}", VARS));
        assertEquals("", TemplateRenderer.render("${unknown}", VARS));
    }

    @Test
    void handlesPlainAndEdgeTemplates() {
        assertEquals("no placeholder", TemplateRenderer.render("no placeholder", VARS));
        assertEquals("", TemplateRenderer.render(null, VARS));
        assertEquals("", TemplateRenderer.render("", VARS));
        assertEquals("abc${", TemplateRenderer.render("abc${", VARS), "未闭合占位按原文输出");
        assertEquals("abc${name", TemplateRenderer.render("abc${name", VARS));
    }

    @Test
    void detectsVariablePresence() {
        assertTrue(TemplateRenderer.contains("${samplesJson}", "samplesJson"));
        assertFalse(TemplateRenderer.contains("plain", "samplesJson"));
        assertFalse(TemplateRenderer.contains(null, "samplesJson"));
    }
}
