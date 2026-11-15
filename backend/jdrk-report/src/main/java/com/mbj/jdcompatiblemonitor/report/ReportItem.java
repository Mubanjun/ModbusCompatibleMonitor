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
 * 一条待上报的采样数据（对应目标库 tbhistory 的一行）。
 *
 * @param deviceAddr   设备地址（从站地址）
 * @param deviceName   设备名称
 * @param nodeId       节点编号（通道号）
 * @param analogNo     模拟量序号（1 或 2）
 * @param elementType  要素类型
 * @param elementName  要素名称
 * @param unit         单位
 * @param value        工程值
 * @param rawValue     原始值
 * @param collectTime  采集时间（毫秒时间戳）
 * @param alarm        是否为报警数据
 * @param lng          经度，可为空
 * @param lat          纬度，可为空
 */
public record ReportItem(int deviceAddr, String deviceName, int nodeId, int analogNo,
    String elementType, String elementName, String unit, double value, double rawValue,
    long collectTime, boolean alarm, Double lng, Double lat) {
}
