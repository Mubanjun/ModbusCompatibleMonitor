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

package com.mbj.jdcompatiblemonitor.device.runtime;

import com.mbj.jdcompatiblemonitor.device.ElementType;

/**
 * 一次采集产出的单条样本，携带落地所需的全部上下文（FR-WEB-01/05、DR-02）。
 *
 * @param portId           端口 ID
 * @param portName         端口地址
 * @param deviceId         设备 ID
 * @param deviceName       设备名称
 * @param deviceAddr       从站地址
 * @param channelNo        通道号
 * @param analogNo         模拟量序号
 * @param elementType      要素类型
 * @param elementName      要素名称
 * @param unit             单位
 * @param rawValue         原始值
 * @param engineeringValue 工程值
 * @param collectTime      采集时间（毫秒时间戳）
 * @param lowerLimit       量程下限，可为空
 * @param upperLimit       量程上限，可为空
 */
public record AcquisitionSample(long portId, String portName, long deviceId, String deviceName,
    int deviceAddr, int channelNo, int analogNo, ElementType elementType, String elementName,
    String unit, double rawValue, double engineeringValue, long collectTime, Double lowerLimit,
    Double upperLimit) {

    /** 工程值是否落在量程内（无上下限配置时视为在量程内）。 */
    public boolean withinRange() {
        if (lowerLimit != null && engineeringValue < lowerLimit) {
            return false;
        }
        return upperLimit == null || !(engineeringValue > upperLimit);
    }

    /** 是否超出量程。 */
    public boolean overRange() {
        return !this.withinRange();
    }
}
