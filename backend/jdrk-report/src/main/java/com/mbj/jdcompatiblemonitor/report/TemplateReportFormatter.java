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

import cn.hutool.json.JSONArray;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义模板报文格式化（FR-RPT-02）。
 *
 * <p>模板未使用 {@code ${samplesJson}} 时按「每条数据渲染一次再换行拼接」处理；
 * 使用了 {@code ${samplesJson}} 时整批只渲染一次，其中该变量为样本数组的 JSON。</p>
 */
public final class TemplateReportFormatter implements ReportFormatter {

    @Override
    public ReportFormat format() {
        return ReportFormat.CUSTOM;
    }

    @Override
    public String format(ReportBatch batch, TransformSpec spec, String content) {
        String template = content == null ? "" : content;
        Map<String, Object> batchVariables = new LinkedHashMap<>(ReportVariables.ofBatch(batch));
        JSONArray samples = new JSONArray();
        for (ReportItem item : batch.items()) {
            Map<String, Object> variables =
                FieldTransformer.apply(ReportVariables.ofItem(item), spec);
            samples.add(variables);
        }
        batchVariables.put("samplesJson", samples.toString());
        if (TemplateRenderer.contains(template, "samplesJson")) {
            return TemplateRenderer.render(template, batchVariables);
        }
        StringBuilder builder = new StringBuilder();
        for (ReportItem item : batch.items()) {
            Map<String, Object> variables = new LinkedHashMap<>(batchVariables);
            variables.putAll(FieldTransformer.apply(ReportVariables.ofItem(item), spec));
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(TemplateRenderer.render(template, variables));
        }
        return builder.toString();
    }
}
