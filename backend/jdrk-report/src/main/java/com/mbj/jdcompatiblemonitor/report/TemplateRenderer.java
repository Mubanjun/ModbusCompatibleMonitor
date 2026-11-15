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

import java.util.Map;

/**
 * 模板渲染（FR-RPT-01 主题模板、FR-RPT-02 自定义报文）。
 *
 * <p>支持 {@code ${变量名}} 占位；变量缺失时替换为空串，不抛异常，
 * 避免因个别字段缺失导致整批上报失败。</p>
 */
public final class TemplateRenderer {

    private TemplateRenderer() {
    }

    /**
     * 渲染模板
     *
     * @param template  模板文本，可为空
     * @param variables 变量表
     * @return 渲染结果
     */
    public static String render(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(template.length() + 32);
        int index = 0;
        while (index < template.length()) {
            int start = template.indexOf("${", index);
            if (start < 0) {
                result.append(template, index, template.length());
                break;
            }
            int end = template.indexOf('}', start + 2);
            if (end < 0) {
                result.append(template, index, template.length());
                break;
            }
            result.append(template, index, start);
            String name = template.substring(start + 2, end).trim();
            Object value = variables == null ? null : variables.get(name);
            result.append(value == null ? "" : value);
            index = end + 1;
        }
        return result.toString();
    }

    /**
     * 判断模板是否包含指定变量
     *
     * @param template 模板
     * @param name     变量名
     * @return 是否包含
     */
    public static boolean contains(String template, String name) {
        return template != null && template.contains("${" + name + "}");
    }
}
