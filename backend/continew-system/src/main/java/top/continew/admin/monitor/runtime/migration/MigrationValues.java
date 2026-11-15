/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
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

package top.continew.admin.monitor.runtime.migration;

import java.time.LocalDateTime;

/**
 * 迁移取值的类型转换工具（源库列值 -> 长整型 / 时间 / 字符串）
 *
 * <p>源库由现场决定，列类型不可控，因此这里对 null、数字、字符串、Timestamp 都做兼容；
 * 时间字符串同时接受 {@code yyyy-MM-dd HH:mm:ss} 与 ISO 的 {@code T} 分隔形式。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
public final class MigrationValues {

    private MigrationValues() {
    }

    /**
     * 取长整型，取不到用默认值
     *
     * @param value        源值
     * @param defaultValue 默认值
     * @return 长整型
     */
    public static long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 取记录时间
     *
     * @param value 源值
     * @return 时间
     */
    public static LocalDateTime toTime(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("记录时间为空");
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    /**
     * 取字符串（null 保持 null）
     *
     * @param value 源值
     * @return 字符串
     */
    public static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
