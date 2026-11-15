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

package top.continew.admin.monitor.runtime.alarm;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报警状态登记表：记录每个「设备 + 通道 + 模拟量」最近一次报警的起始时间。
 *
 * <p>落地线程据此在写入历史数据时打上 {@code alarm_flag}，避免在落库路径反复查询报警记录
 * （FR-ALM-02 报警数值需要能标识是否越限）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Component
public class AlarmStateRegistry {

    private final Map<String, Long> alarmedSince = new ConcurrentHashMap<>();

    /**
     * 标记某个采样点进入报警态
     *
     * @param deviceId    设备 ID
     * @param channelNo   通道号
     * @param analogNo    模拟量序号
     * @param nowMillis   报警时间
     */
    public void markAlarmed(long deviceId, int channelNo, int analogNo, long nowMillis) {
        alarmedSince.putIfAbsent(this.key(deviceId, channelNo, analogNo), nowMillis);
    }

    /**
     * 标记某个采样点恢复
     *
     * @param deviceId  设备 ID
     * @param channelNo 通道号
     * @param analogNo  模拟量序号
     */
    public void markRecovered(long deviceId, int channelNo, int analogNo) {
        alarmedSince.remove(this.key(deviceId, channelNo, analogNo));
    }

    /**
     * 判断某条采集数据是否处于报警区间
     *
     * @param deviceId    设备 ID
     * @param channelNo   通道号
     * @param analogNo    模拟量序号
     * @param collectTime 采集时间（毫秒时间戳）
     * @return 处于报警区间返回 true
     */
    public boolean alarmedAt(long deviceId, int channelNo, int analogNo, long collectTime) {
        Long since = alarmedSince.get(this.key(deviceId, channelNo, analogNo));
        return since != null && collectTime >= since;
    }

    /**
     * 当前处于报警态的采样点数量
     *
     * @return 数量
     */
    public int activeCount() {
        return alarmedSince.size();
    }

    private String key(long deviceId, int channelNo, int analogNo) {
        return deviceId + ":" + channelNo + ":" + analogNo;
    }
}
