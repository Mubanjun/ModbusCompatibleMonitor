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
 * 一个设备的调度档案（FR-ACQ-01/04/08/11）。
 *
 * <p>排序规则：高优先级设备优先，其次按端口内采集顺序（{@code sortNo}）升序，
 * 最后以设备 ID 兜底，保证顺序稳定、可复现。</p>
 *
 * @param deviceId   设备 ID
 * @param sortNo     端口内采集顺序
 * @param priority   是否高优先级设备
 * @param enabled    是否参与轮询
 * @param intervalMs 生效的采集间隔（毫秒）
 */
public record DeviceSchedule(long deviceId, int sortNo, boolean priority, boolean enabled,
    int intervalMs) implements Comparable<DeviceSchedule> {

    @Override
    public int compareTo(DeviceSchedule other) {
        if (priority != other.priority) {
            return priority ? -1 : 1;
        }
        int bySort = Integer.compare(sortNo, other.sortNo);
        return bySort != 0 ? bySort : Long.compare(deviceId, other.deviceId);
    }

    /**
     * 判断设备本轮是否到期
     *
     * @param lastPollTime 上次轮询时间，0 表示尚未轮询过
     * @param nowMillis    当前时间
     * @param intervalMs   生效间隔
     * @return 到期返回 true
     */
    public static boolean isDue(long lastPollTime, long nowMillis, int intervalMs) {
        if (!(intervalMs > 0)) {
            return true;
        }
        return lastPollTime <= 0L || nowMillis - lastPollTime >= intervalMs;
    }

    /**
     * 判断本设备自身是否到期
     *
     * @param lastPollTime 上次轮询时间
     * @param nowMillis    当前时间
     * @return 到期返回 true
     */
    public boolean isDue(long lastPollTime, long nowMillis) {
        return enabled && isDue(lastPollTime, nowMillis, intervalMs);
    }
}
