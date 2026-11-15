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

package com.mbj.jdcompatiblemonitor.sync;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条待同步/已存在的目标库记录（FR-SYNC-02/03）。
 *
 * @param id         主键值（目标库 ID 为字符串）
 * @param recordTime 记录时间，用于新旧判定与增量水位
 * @param values     列名到值的映射（不含主键，可为空表示仅用于比较）
 */
public record SyncRecord(String id, LocalDateTime recordTime, Map<String, Object> values) {

    public SyncRecord {
        // 目标库允许列值为 null（例如仅启用模拟量1 时 Hum 为空），因此不能用 Map.copyOf
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(
            values));
    }

    /**
     * 构造仅含主键与时间的记录（目标存在性查询结果）
     *
     * @param id         主键
     * @param recordTime 记录时间
     * @return 记录
     */
    public static SyncRecord of(String id, LocalDateTime recordTime) {
        return new SyncRecord(id, recordTime, Map.of());
    }

    /**
     * 按列顺序取写入值
     *
     * @param table 目标表
     * @return 与 {@link SyncTable#valueColumns()} 一一对应的值数组
     */
    public Object[] orderedValues(SyncTable table) {
        Object[] result = new Object[table.valueColumns().size()];
        int index = 0;
        for (String column : table.valueColumns()) {
            result[index++] = values.get(column);
        }
        return result;
    }
}
