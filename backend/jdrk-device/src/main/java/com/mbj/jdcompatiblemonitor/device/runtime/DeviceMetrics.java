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
 * 单设备采集指标（FR-ACQ-10：轮询成功率、平均耗时、超时次数、最后通讯时间）。
 *
 * <p>计数器为就地累加，方法整体加锁；采集规模为「16 端口 × 32 从站」，
 * 锁竞争远低于网络往返耗时可忽略（NFR-PERF-04）。</p>
 */
public final class DeviceMetrics {

    private long pollCount;
    private long successCount;
    private long timeoutCount;
    private long errorCount;
    private long totalDurationMs;
    private long lastPollTime;
    private long lastSuccessTime;
    private long lastFailureTime;
    private String lastError;

    /**
     * 记录一次轮询开始
     *
     * @param nowMillis 当前时间
     */
    public synchronized void recordPoll(long nowMillis) {
        pollCount++;
        lastPollTime = nowMillis;
    }

    /**
     * 记录一次成功
     *
     * @param nowMillis  当前时间
     * @param durationMs 耗时
     */
    public synchronized void recordSuccess(long nowMillis, long durationMs) {
        successCount++;
        totalDurationMs += Math.max(durationMs, 0L);
        lastSuccessTime = nowMillis;
        lastError = null;
    }

    /**
     * 记录一次失败
     *
     * @param nowMillis  当前时间
     * @param durationMs 耗时
     * @param timeout    是否超时
     * @param error      失败原因
     */
    public synchronized void recordFailure(long nowMillis, long durationMs, boolean timeout,
        String error) {
        errorCount++;
        if (timeout) {
            timeoutCount++;
        }
        totalDurationMs += Math.max(durationMs, 0L);
        lastFailureTime = nowMillis;
        lastError = error;
    }

    /** 清空全部计数（重新加载配置时调用）。 */
    public synchronized void reset() {
        pollCount = 0;
        successCount = 0;
        timeoutCount = 0;
        errorCount = 0;
        totalDurationMs = 0;
        lastPollTime = 0;
        lastSuccessTime = 0;
        lastFailureTime = 0;
        lastError = null;
    }

    /** 轮询总次数。 */
    public synchronized long pollCount() {
        return pollCount;
    }

    /** 成功次数。 */
    public synchronized long successCount() {
        return successCount;
    }

    /** 超时次数。 */
    public synchronized long timeoutCount() {
        return timeoutCount;
    }

    /** 失败次数（含超时）。 */
    public synchronized long errorCount() {
        return errorCount;
    }

    /** 轮询成功率，无数据时返回 0。 */
    public synchronized double successRate() {
        return pollCount == 0 ? 0d : (double) successCount / (double) pollCount;
    }

    /** 平均耗时（毫秒），无数据时返回 0。 */
    public synchronized long averageDurationMs() {
        long finished = successCount + errorCount;
        return finished == 0 ? 0L : totalDurationMs / finished;
    }

    /** 最后一次轮询时间。 */
    public synchronized long lastPollTime() {
        return lastPollTime;
    }

    /** 最后一次通讯成功时间。 */
    public synchronized long lastSuccessTime() {
        return lastSuccessTime;
    }

    /** 最后一次失败时间。 */
    public synchronized long lastFailureTime() {
        return lastFailureTime;
    }

    /** 最后一次失败原因。 */
    public synchronized String lastError() {
        return lastError;
    }
}
