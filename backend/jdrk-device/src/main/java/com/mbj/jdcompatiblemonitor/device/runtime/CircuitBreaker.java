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
 * 设备级熔断器（FR-PROTO-07「连续失败触发退避与熔断，恢复后自动回归」、
 * FR-ACQ-09「连续失败达阈值将设备置为异常并降低轮询频率」）。
 *
 * <p>纯逻辑实现，不依赖时钟：所有判定都由调用方传入 {@code nowMillis}，
 * 因此可以完全用单元测试覆盖状态迁移。</p>
 *
 * <p>状态迁移：CLOSED --连续失败达阈值--> OPEN --openMillis 期满--> HALF_OPEN
 * --成功--> CLOSED / --失败--> OPEN。</p>
 */
public final class CircuitBreaker {

    private final int failureThreshold;
    private final long openMillis;
    private final long maxBackoffMillis;

    private CircuitState state = CircuitState.CLOSED;
    private int consecutiveFailures;
    private long openUntil;
    private int tripCount;

    /**
     * 构造熔断器
     *
     * @param failureThreshold 连续失败阈值
     * @param openMillis       熔断打开时长（毫秒）
     * @param maxBackoffMillis 退避间隔上限（毫秒）
     */
    public CircuitBreaker(int failureThreshold, long openMillis, long maxBackoffMillis) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openMillis = Math.max(1L, openMillis);
        this.maxBackoffMillis = Math.max(1L, maxBackoffMillis);
    }

    /**
     * 当前是否允许发起请求
     *
     * @param nowMillis 当前时间
     * @return 允许返回 true；熔断期内返回 false
     */
    public synchronized boolean allowRequest(long nowMillis) {
        if (state == CircuitState.OPEN) {
            if (nowMillis >= openUntil) {
                state = CircuitState.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * 记录一次成功：无论此前为何状态都回归正常
     */
    public synchronized void recordSuccess() {
        state = CircuitState.CLOSED;
        consecutiveFailures = 0;
        openUntil = 0L;
    }

    /**
     * 记录一次失败
     *
     * @param nowMillis 当前时间
     */
    public synchronized void recordFailure(long nowMillis) {
        consecutiveFailures++;
        boolean trip = state == CircuitState.HALF_OPEN
            || consecutiveFailures >= failureThreshold;
        if (!trip) {
            return;
        }
        state = CircuitState.OPEN;
        openUntil = nowMillis + openMillis;
        tripCount++;
    }

    /**
     * 失败期间的退避间隔
     *
     * @param baseIntervalMs 基础采集间隔
     * @return 退避后的间隔，不超过 {@code maxBackoffMillis}
     */
    public synchronized int backoffInterval(int baseIntervalMs) {
        if (consecutiveFailures == 0 && state == CircuitState.CLOSED) {
            return baseIntervalMs;
        }
        int shift = Math.min(consecutiveFailures, 16);
        long multiplied = (long) baseIntervalMs << shift;
        // 退避只会拉长不会缩短：上限低于基础间隔时以上限无效处理
        long cap = Math.max(maxBackoffMillis, baseIntervalMs);
        return (int) Math.min(Math.max(multiplied, baseIntervalMs), cap);
    }

    /** 当前状态。 */
    public synchronized CircuitState state() {
        return state;
    }

    /** 连续失败次数。 */
    public synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }

    /** 熔断触发累计次数。 */
    public synchronized int tripCount() {
        return tripCount;
    }

    /**
     * 熔断解除时间（{@code state} 非 OPEN 时无意义）
     *
     * @return 解除时间（毫秒时间戳）
     */
    public synchronized long openUntil() {
        return openUntil;
    }

    /** 是否处于正常状态。 */
    public synchronized boolean isHealthy() {
        return state == CircuitState.CLOSED;
    }
}
