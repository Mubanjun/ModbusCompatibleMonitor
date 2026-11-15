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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 字段映射、单位换算、脱敏与裁剪规则（FR-RPT-06）。
 *
 * <p>由上报目标的 {@code config_json} 装配，形如：</p>
 *
 * <pre>
 * {
 *   "mapping": { "deviceName": "pointName" },
 *   "units":   { "value": { "factor": 0.001, "offset": 0 } },
 *   "mask":    ["deviceName"],
 *   "include": ["deviceName", "value"],
 *   "exclude": ["rawValue"]
 * }
 * </pre>
 *
 * @param mapping 字段改名（原字段名 -> 目标字段名）
 * @param units   字段单位换算
 * @param mask    需要脱敏的字段（值替换为 ***）
 * @param include 白名单，非空时只保留这些字段
 * @param exclude 黑名单，命中即剔除
 */
public record TransformSpec(Map<String, String> mapping, Map<String, UnitConvert> units,
    Set<String> mask, Set<String> include, Set<String> exclude) {

    /** 脱敏后的占位值 */
    public static final String MASKED = "***";

    public TransformSpec {
        mapping = mapping == null ? Map.of() : Map.copyOf(mapping);
        units = units == null ? Map.of() : Map.copyOf(units);
        mask = mask == null ? Set.of() : Set.copyOf(mask);
        include = include == null ? Set.of() : Set.copyOf(include);
        exclude = exclude == null ? Set.of() : Set.copyOf(exclude);
    }

    /** 空规则：不做任何变换。 */
    public static TransformSpec empty() {
        return new TransformSpec(Map.of(), Map.of(), Set.of(), Set.of(), Set.of());
    }

    /**
     * 是否为无操作规则
     *
     * @return 无任何映射、换算、脱敏与裁剪返回 true
     */
    public boolean isEmpty() {
        return mapping.isEmpty() && units.isEmpty() && mask.isEmpty() && include.isEmpty()
            && exclude
                .isEmpty();
    }

    /**
     * 从连接参数 JSON 解析变换规则
     *
     * @param configJson 连接参数（可为空或非 JSON）
     * @return 变换规则，解析失败返回空规则
     */
    public static TransformSpec parse(String configJson) {
        if (configJson == null || configJson.isBlank() || !JSONUtil.isTypeJSON(configJson)) {
            return empty();
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(configJson);
        } catch (Exception e) {
            return empty();
        }
        Map<String, String> mapping = new HashMap<>();
        JSONObject mappingJson = json.getJSONObject("mapping");
        if (mappingJson != null) {
            for (String key : mappingJson.keySet()) {
                mapping.put(key, String.valueOf(mappingJson.get(key)));
            }
        }
        Map<String, UnitConvert> units = new HashMap<>();
        JSONObject unitsJson = json.getJSONObject("units");
        if (unitsJson != null) {
            for (String key : unitsJson.keySet()) {
                JSONObject item = unitsJson.getJSONObject(key);
                if (item == null) {
                    continue;
                }
                units.put(key, new UnitConvert(item.getDouble("factor", 1.0D), item
                    .getDouble("offset", 0.0D)));
            }
        }
        return new TransformSpec(mapping, units, toSet(json.getJSONArray("mask")), toSet(json
            .getJSONArray("include")), toSet(json.getJSONArray("exclude")));
    }

    private static Set<String> toSet(cn.hutool.json.JSONArray array) {
        Set<String> values = new HashSet<>();
        if (array == null) {
            return values;
        }
        for (Object item : array) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }
}
