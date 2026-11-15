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

/** 熔断器状态（FR-PROTO-07 / FR-ACQ-09）。 */
public enum CircuitState {

    /** 正常：允许采集。 */
    CLOSED,

    /** 熔断：连续失败达阈值，暂停采集。 */
    OPEN,

    /** 半开：熔断期满后放行一次探测。 */
    HALF_OPEN
}
