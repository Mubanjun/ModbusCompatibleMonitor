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

/** 设备运行状态（FR-WEB-03：在线、离线、停用、报警、通讯异常）。 */
public enum DeviceStatus {

    /** 尚未采集过 */
    UNKNOWN("未知"),

    /** 在线 */
    ONLINE("在线"),

    /** 离线（熔断中或长时间无成功通讯） */
    OFFLINE("离线"),

    /** 停用（配置为不启用，不参与轮询） */
    DISABLED("停用"),

    /** 报警 */
    ALARM("报警"),

    /** 通讯异常（连续失败但尚未熔断） */
    COMM_ERROR("通讯异常");

    private final String label;

    DeviceStatus(String label) {
        this.label = label;
    }

    /** 中文展示名。 */
    public String label() {
        return label;
    }
}
