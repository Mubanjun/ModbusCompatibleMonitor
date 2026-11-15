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
 * 上报报文格式化（FR-RPT-02）。
 *
 * <p>实现需保证：相同输入产生相同输出，且不修改入参。</p>
 */
public interface ReportFormatter {

    /**
     * 支持的报文格式
     *
     * @return 报文格式
     */
    ReportFormat format();

    /**
     * 格式化一个批次
     *
     * @param batch   批次
     * @param spec    字段变换规则
     * @param content 自定义模板内容（仅 CUSTOM 使用，可为空）
     * @return 报文文本
     */
    String format(ReportBatch batch, TransformSpec spec, String content);
}
