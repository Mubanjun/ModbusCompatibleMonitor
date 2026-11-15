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
import java.util.StringJoiner;

/**
 * 键值对报文格式化（FR-RPT-02）。
 *
 * <p>每条数据一行，行内为 {@code key=value}，字段之间用 {@code ;} 分隔。</p>
 */
public final class KvReportFormatter implements ReportFormatter {

    @Override
    public ReportFormat format() {
        return ReportFormat.KV;
    }

    @Override
    public String format(ReportBatch batch, TransformSpec spec, String content) {
        StringBuilder builder = new StringBuilder();
        Map<String, Object> batchVariables = ReportVariables.ofBatch(batch);
        for (ReportItem item : batch.items()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            StringJoiner joiner = new StringJoiner(";");
            for (Map.Entry<String, Object> entry : batchVariables.entrySet()) {
                if (!"batchTimestamp".equals(entry.getKey())) {
                    joiner.add(entry.getKey() + "=" + entry.getValue());
                }
            }
            Map<String, Object> variables =
                FieldTransformer.apply(ReportVariables.ofItem(item), spec);
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                joiner.add(entry.getKey() + "=" + entry.getValue());
            }
            builder.append(joiner);
        }
        return builder.toString();
    }
}
