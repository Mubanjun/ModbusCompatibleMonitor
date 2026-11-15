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
 * 字段单位换算（FR-RPT-06：单位换算）
 *
 * @param factor 倍数
 * @param offset 偏移
 */
public record UnitConvert(double factor, double offset) {

    /**
     * 应用换算
     *
     * @param value 原值
     * @return 换算后的值
     */
    public double apply(double value) {
        return value * factor + offset;
    }
}
