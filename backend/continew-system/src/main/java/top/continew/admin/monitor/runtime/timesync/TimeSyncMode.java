/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
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

package top.continew.admin.monitor.runtime.timesync;

/**
 * 校时方式（NFR-REL-06 可选择性校时）
 *
 * @author jdrk
 * @since 2026/10/1
 */
public enum TimeSyncMode {

    /**
     * 全部下发：不做判断，逐台写入平台本机时间
     */
    ALL,

    /**
     * 仅超差下发：偏差在容差内的设备跳过写入（自动定时校时即采用该方式）
     */
    TOLERANT;

    /**
     * 解析校时方式，空值按 {@link #ALL} 处理
     *
     * @param value 取值（ALL/TOLERANT，忽略大小写）
     * @return 校时方式
     */
    public static TimeSyncMode of(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        for (TimeSyncMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知的校时方式：" + value);
    }
}
