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

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.Map;

/**
 * JSON 报文格式化（FR-RPT-02 默认格式）。
 *
 * <pre>
 * {"ruleId":1,"ruleName":"..","targetName":"..","portName":"COM1",
 *  "batchTime":"2026-09-23 10:00:00","count":2,
 *  "samples":[{"deviceAddr":1,"nodeId":1,"value":7.42,...}]}
 * </pre>
 */
public final class JsonReportFormatter implements ReportFormatter {

    @Override
    public ReportFormat format() {
        return ReportFormat.JSON;
    }

    @Override
    public String format(ReportBatch batch, TransformSpec spec, String content) {
        JSONObject root = new JSONObject();
        Map<String, Object> batchVariables = ReportVariables.ofBatch(batch);
        for (Map.Entry<String, Object> entry : batchVariables.entrySet()) {
            // 同时保留可读时间与毫秒时间戳，便于死信重放时无损还原
            root.set(entry.getKey(), entry.getValue());
        }
        for (ReportItem item : batch.items()) {
            root.append("samples", this.toJson(item, spec));
        }
        if (batch.items().isEmpty()) {
            root.set("samples", JSONUtil.createArray());
        }
        return root.toString();
    }

    private JSONObject toJson(ReportItem item, TransformSpec spec) {
        Map<String, Object> variables = FieldTransformer.apply(ReportVariables.ofItem(item), spec);
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            json.set(entry.getKey(), entry.getValue());
        }
        return json;
    }
}
