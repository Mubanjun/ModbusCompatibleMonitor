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

import java.util.EnumMap;
import java.util.Map;

/**
 * 报文格式化器注册表（FR-RPT-02、NFR-SCALE-02：新增格式只需新增实现）。
 */
public final class ReportFormatters {

    private static final Map<ReportFormat, ReportFormatter> FORMATTERS =
        new EnumMap<>(ReportFormat.class);

    static {
        register(new JsonReportFormatter());
        register(new KvReportFormatter());
        register(new TemplateReportFormatter());
    }

    private ReportFormatters() {
    }

    /**
     * 注册格式化器
     *
     * @param formatter 格式化器
     */
    public static void register(ReportFormatter formatter) {
        FORMATTERS.put(formatter.format(), formatter);
    }

    /**
     * 取格式化器
     *
     * @param format 报文格式
     * @return 格式化器，未注册时回退到 JSON
     */
    public static ReportFormatter of(ReportFormat format) {
        ReportFormatter formatter = FORMATTERS.get(format);
        return formatter == null ? FORMATTERS.get(ReportFormat.JSON) : formatter;
    }

    /**
     * 按名称取格式化器
     *
     * @param format 报文格式名称
     * @return 格式化器
     */
    public static ReportFormatter of(String format) {
        return of(ReportFormat.parse(format));
    }
}
