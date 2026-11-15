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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 字段变换执行器（FR-RPT-06）。
 *
 * <p>顺序：裁剪（include/exclude）→ 单位换算 → 改名 → 脱敏。</p>
 */
public final class FieldTransformer {

    private FieldTransformer() {
    }

    /**
     * 应用变换规则
     *
     * @param source 原始字段
     * @param spec   变换规则
     * @return 变换后的字段（保持原顺序）
     */
    public static Map<String, Object> apply(Map<String, Object> source, TransformSpec spec) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        if (spec == null || spec.isEmpty()) {
            return new LinkedHashMap<>(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String name = entry.getKey();
            if (!spec.include().isEmpty() && !spec.include().contains(name)) {
                continue;
            }
            if (spec.exclude().contains(name)) {
                continue;
            }
            Object value = entry.getValue();
            UnitConvert convert = spec.units().get(name);
            if (convert != null && value instanceof Number number) {
                value = convert.apply(number.doubleValue());
            }
            if (spec.mask().contains(name)) {
                value = TransformSpec.MASKED;
            }
            result.put(spec.mapping().getOrDefault(name, name), value);
        }
        return result;
    }
}
