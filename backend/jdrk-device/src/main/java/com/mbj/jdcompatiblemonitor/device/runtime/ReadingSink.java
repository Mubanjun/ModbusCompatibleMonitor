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

/**
 * 采集结果消费者。
 *
 * <p>采集内核只负责把样本推给本接口，落地、报警、上报等能力各自实现一个消费者，
 * 从而满足 NFR-MAINT-04「上层依赖下层，模块通过接口交互」。</p>
 */
@FunctionalInterface
public interface ReadingSink {

    /**
     * 消费一条采集样本
     *
     * @param sample 样本
     */
    void accept(AcquisitionSample sample);

    /**
     * 通知设备状态变化（FR-WEB-03 状态标识）
     *
     * <p>默认实现忽略；落地实现可据此把设备状态同步到实时值表。</p>
     *
     * @param portId   端口 ID
     * @param deviceId 设备 ID
     * @param status   设备状态
     */
    default void onDeviceState(long portId, long deviceId, DeviceStatus status) {
    }
}
