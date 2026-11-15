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

package top.continew.admin.monitor.runtime.session;

import com.mbj.jdcompatiblemonitor.device.runtime.CircuitBreaker;
import com.mbj.jdcompatiblemonitor.device.runtime.CircuitState;
import com.mbj.jdcompatiblemonitor.device.runtime.DeviceMetrics;
import com.mbj.jdcompatiblemonitor.device.runtime.DeviceStatus;
import lombok.Getter;
import top.continew.admin.monitor.model.entity.BizDeviceDO;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备会话：一个从站（在仁科主机场景下即一个通道）的运行态。
 *
 * <p>持有熔断器、采集指标、最后通讯时间与状态，调度只读写这些运行态。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Getter
public final class DeviceSession implements Comparable<DeviceSession> {

    private final BizDeviceDO device;
    private final int sortNo;
    private final boolean enabled;
    private final List<ChannelBinding> channels;
    private final CircuitBreaker breaker;
    private final DeviceMetrics metrics = new DeviceMetrics();
    private final AtomicLong lastPollTime = new AtomicLong(0L);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean budgetExceeded = new AtomicBoolean(false);
    private volatile DeviceStatus status = DeviceStatus.UNKNOWN;
    private volatile long lastReloadTime = System.currentTimeMillis();

    public DeviceSession(BizDeviceDO device, int sortNo, boolean enabled,
        List<ChannelBinding> channels, CircuitBreaker breaker) {
        this.device = device;
        this.sortNo = sortNo;
        this.enabled = enabled;
        this.channels = List.copyOf(channels);
        this.breaker = breaker;
        this.status = Boolean.FALSE.equals(device.getEnabled()) ? DeviceStatus.DISABLED
            : DeviceStatus.UNKNOWN;
    }

    /**
     * 设备 ID
     *
     * @return 设备 ID
     */
    public long deviceId() {
        return device.getId();
    }

    /**
     * 从站地址
     *
     * @return 从站地址
     */
    public int slaveAddr() {
        return device.getSlaveAddr() == null ? 1 : device.getSlaveAddr();
    }

    /**
     * 是否启用（设备启用且采集顺序启用）
     *
     * @return 是否启用
     */
    public boolean isActive() {
        return enabled && Boolean.TRUE.equals(device.getEnabled()) && !paused.get();
    }

    /**
     * 记录一次轮询时间
     *
     * @param nowMillis 当前时间
     */
    public void markPolled(long nowMillis) {
        lastPollTime.set(nowMillis);
    }

    /**
     * 记录超预算跳过（FR-ACQ-02）
     */
    public void markBudgetExceeded() {
        budgetExceeded.set(true);
    }

    /**
     * 按熔断与指标推导设备状态（FR-ACQ-09 / FR-WEB-03）
     */
    public void refreshStatus() {
        if (Boolean.FALSE.equals(device.getEnabled())) {
            status = DeviceStatus.DISABLED;
            return;
        }
        if (breaker.state() == CircuitState.OPEN) {
            status = DeviceStatus.OFFLINE;
            return;
        }
        if (breaker.consecutiveFailures() > 0) {
            status = DeviceStatus.COMM_ERROR;
            return;
        }
        status = metrics.lastSuccessTime() > 0 ? DeviceStatus.ONLINE : DeviceStatus.UNKNOWN;
    }

    @Override
    public int compareTo(DeviceSession other) {
        boolean highPriority = Integer.valueOf(1).equals(device.getPriority());
        boolean otherHighPriority = Integer.valueOf(1).equals(other.device.getPriority());
        if (highPriority != otherHighPriority) {
            return highPriority ? -1 : 1;
        }
        int bySort = Integer.compare(sortNo, other.sortNo);
        return bySort != 0 ? bySort : Long.compare(deviceId(), other.deviceId());
    }
}
