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

/**
 * 上报报文格式（FR-RPT-02，与 {@code biz_report_target.format_type} 取值一致）。
 */
public enum ReportFormat {

    /** JSON（默认） */
    JSON("JSON"),

    /** 键值对 */
    KV("键值对"),

    /** 自定义模板 */
    CUSTOM("自定义模板");

    private final String label;

    ReportFormat(String label) {
        this.label = label;
    }

    /** 中文名称。 */
    public String label() {
        return label;
    }

    /**
     * 宽松解析
     *
     * @param value 字符串
     * @return 格式，无法识别返回 {@link #JSON}
     */
    public static ReportFormat parse(String value) {
        if (value == null || value.isBlank()) {
            return JSON;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return JSON;
        }
    }
}
