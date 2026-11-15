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

package com.mbj.jdcompatiblemonitor.device;

/** 水质采集要素。名称与单位取自厂家 485 型变送器说明书。 */
public enum ElementType {

    TEMPERATURE("温度", "℃"),
    PH("PH", "pH"),
    ORP("ORP", "mV"),
    CONDUCTIVITY("电导率", "us/cm"),
    WIDE_RANGE_CONDUCTIVITY("宽量程电导率", "us/cm"),
    DISSOLVED_OXYGEN("溶解氧", "mg/L"),
    TURBIDITY("浊度", "NTU"),
    RESIDUAL_CHLORINE("余氯", "mg/L"),
    AMMONIA_NITROGEN("氨氮", "mg/L"),
    ION("离子", "mg/L"),
    COD("COD", "mg/L"),
    CHLOROPHYLL("叶绿素", "ug/L"),
    BLUE_GREEN_ALGAE("蓝绿藻", "cells/ml"),
    SUSPENDED_SOLIDS("悬浮物", "mg/L"),
    OIL_IN_WATER("水中油", "ppm"),
    COLORITY("色度", "Hazen");

    private final String displayName;
    private final String unit;

    ElementType(String displayName, String unit) {
        this.displayName = displayName;
        this.unit = unit;
    }

    public String displayName() {
        return displayName;
    }

    public String unit() {
        return unit;
    }
}
