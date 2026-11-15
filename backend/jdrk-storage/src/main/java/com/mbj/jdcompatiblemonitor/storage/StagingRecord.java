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

package com.mbj.jdcompatiblemonitor.storage;

/**
 * 本地暂存中的一条采样记录，对应 DR-01 的 {@code t_sample_buffer}。
 *
 * @param id               暂存自增主键（新增时为 0）
 * @param portId           端口 ID
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
 * @param quality          数据质量（GOOD / BAD / STALE）
 * @param overRange        是否超量程
 * @param collectTime      采集时间（毫秒时间戳）
 */
public record StagingRecord(long id, long portId, long deviceId, String deviceName, int deviceAddr,
    int channelNo, int analogNo, String elementType, String elementName, String unit,
    double rawValue, double engineeringValue, String quality, boolean overRange,
    long collectTime) {

    /** 数据质量：正常。 */
    public static final String QUALITY_GOOD = "GOOD";
    /** 数据质量：异常。 */
    public static final String QUALITY_BAD = "BAD";

    /**
     * 返回带暂存主键的副本
     *
     * @param newId 暂存主键
     * @return 新记录
     */
    public StagingRecord withId(long newId) {
        return new StagingRecord(newId, portId, deviceId, deviceName, deviceAddr, channelNo,
            analogNo, elementType, elementName, unit, rawValue, engineeringValue, quality,
            overRange, collectTime);
    }
}
