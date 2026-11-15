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

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.model.entity.BizAlarmNotifyDO;
import top.continew.admin.monitor.runtime.service.RuntimeSettingsService;
import top.continew.admin.monitor.service.BizAlarmNotifyService;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 报警通知调度（FR-ALM-04）：按规则配置的方式分发、频率控制、失败重试与死信留存。
 *
 * <p>通知先落库再发送，失败时保留 {@code send_status}/{@code retry_count}/{@code error_msg}，
 * 由后台线程按退避策略重试；超过重试上限的记录保留为失败态供人工查看，不会静默丢失。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmNotifyDispatcher {

    /** 待发送 */
    private static final int STATUS_PENDING = 0;
    /** 发送成功 */
    private static final int STATUS_SUCCESS = 1;
    /** 发送失败 */
    private static final int STATUS_FAILED = 2;

    private final BizAlarmNotifyService bizAlarmNotifyService;
    private final ObjectProvider<AlarmNotifier> notifierProvider;
    private final AlarmProperties properties;
    private final RuntimeSettingsService runtimeSettingsService;

    private final Map<String, AlarmNotifier> channels = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Long>> notifyHistory = new ConcurrentHashMap<>();

    private final AtomicLong dispatchCount = new AtomicLong(0L);
    private final AtomicLong successCount = new AtomicLong(0L);
    private final AtomicLong failureCount = new AtomicLong(0L);
    private final AtomicLong throttledCount = new AtomicLong(0L);
    private final AtomicLong retryCount = new AtomicLong(0L);

    private volatile ScheduledExecutorService scheduler;

    /**
     * 应用就绪后加载通道并启动重试线程
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        for (AlarmNotifier notifier : notifierProvider.orderedStream().toList()) {
            channels.put(notifier.notifyType().toUpperCase(), notifier);
        }
        log.info("报警通知通道：{}", channels.keySet());
        long interval = Math.max(properties.getNotifyRetryIntervalMillis(), 5000);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "acq-alarm-notify");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::retryPending, interval, interval,
            TimeUnit.MILLISECONDS);
        this.scheduler = executor;
    }

    /**
     * 停止重试线程
     */
    @PreDestroy
    public void stop() {
        ScheduledExecutorService executor = scheduler;
        scheduler = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * 按规则配置的通知方式分发
     *
     * @param message       通知内容
     * @param notifyMethods 逗号分隔的通知方式
     */
    public void dispatch(AlarmNotifyMessage message, String notifyMethods) {
        if (StrUtil.isBlank(notifyMethods)) {
            return;
        }
        for (String raw : StrUtil.splitTrim(notifyMethods, ',')) {
            String type = raw.toUpperCase();
            if (this.throttle(message.ruleId())) {
                throttledCount.incrementAndGet();
                log.warn("报警规则 [{}] 在 {}ms 内通知次数已达上限 {}，本次 {} 通知被限流", message.ruleId(),
                    properties.getNotifyWindowMillis(), properties.getNotifyMaxPerWindow(), type);
                continue;
            }
            BizAlarmNotifyDO row = new BizAlarmNotifyDO();
            row.setCreateUser(0L);
            row.setRecordId(message.recordId());
            row.setNotifyType(type);
            row.setTarget(this.resolveTarget(type));
            row.setContent(message.content());
            row.setRetryCount(0);
            dispatchCount.incrementAndGet();
            this.attempt(row);
            bizAlarmNotifyService.save(row);
        }
    }

    /**
     * 扫描并重试失败通知
     */
    public void retryPending() {
        try {
            List<BizAlarmNotifyDO> pending = bizAlarmNotifyService.lambdaQuery()
                .in(BizAlarmNotifyDO::getSendStatus, STATUS_PENDING, STATUS_FAILED)
                .lt(BizAlarmNotifyDO::getRetryCount, Math.max(properties.getNotifyMaxRetry(), 1))
                .orderByAsc(BizAlarmNotifyDO::getId)
                .last("LIMIT " + Math.max(properties.getNotifyRetryBatchSize(), 1))
                .list();
            for (BizAlarmNotifyDO row : pending) {
                row.setRetryCount((row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1);
                retryCount.incrementAndGet();
                this.attempt(row);
                bizAlarmNotifyService.updateById(row);
            }
        } catch (Exception e) {
            log.debug("报警通知重试扫描失败：{}", e.getMessage());
        }
    }

    /**
     * 组装运行状态
     *
     * @return 状态
     */
    public Map<String, Object> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("channels", channels.keySet());
        stats.put("dispatchCount", dispatchCount.get());
        stats.put("successCount", successCount.get());
        stats.put("failureCount", failureCount.get());
        stats.put("throttledCount", throttledCount.get());
        stats.put("retryCount", retryCount.get());
        stats.put("notifyMaxPerWindow", properties.getNotifyMaxPerWindow());
        stats.put("notifyWindowMillis", properties.getNotifyWindowMillis());
        return stats;
    }

    /**
     * 尝试发送并写回结果
     *
     * @param row 通知记录
     */
    private void attempt(BizAlarmNotifyDO row) {
        AlarmNotifier notifier = channels.get(row.getNotifyType());
        if (notifier == null) {
            row.setSendStatus(STATUS_PENDING);
            row.setErrorMsg("尚未接入该通知通道：" + row.getNotifyType());
            return;
        }
        try {
            notifier.send(row.getTarget(), row.getContent());
            row.setSendStatus(STATUS_SUCCESS);
            row.setSendTime(LocalDateTime.now());
            row.setErrorMsg(null);
            successCount.incrementAndGet();
        } catch (Exception e) {
            row.setSendStatus(STATUS_FAILED);
            row.setErrorMsg(this.truncate(e.getMessage()));
            failureCount.incrementAndGet();
            log.warn("报警通知发送失败（{}）：{}", row.getNotifyType(), e.getMessage());
        }
    }

    /**
     * 解析通知目标
     *
     * @param type 通知方式
     * @return 目标
     */
    private String resolveTarget(String type) {
        return switch (type) {
            case "SMS" ->
                runtimeSettingsService.alarmTarget(RuntimeSettingsService.KEY_ALARM_SMS_TARGET,
                    null);
            case "EMAIL" -> runtimeSettingsService
                .alarmTarget(RuntimeSettingsService.KEY_ALARM_EMAIL_TARGET, null);
            case "MQTT" ->
                runtimeSettingsService.alarmTarget(RuntimeSettingsService.KEY_ALARM_MQTT_TOPIC,
                    "jdrk/alarm");
            default -> null;
        };
    }

    /**
     * 通知频率控制（滑动窗口）
     *
     * @param ruleId 规则 ID
     * @return 是否应限流
     */
    private boolean throttle(long ruleId) {
        if (properties.getNotifyMaxPerWindow() <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        Deque<Long> history = notifyHistory.computeIfAbsent(ruleId, key -> new ArrayDeque<>());
        synchronized (history) {
            while (!history.isEmpty() && now - history.peekFirst() > properties
                .getNotifyWindowMillis()) {
                history.pollFirst();
            }
            if (history.size() >= properties.getNotifyMaxPerWindow()) {
                return true;
            }
            history.addLast(now);
            return false;
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 480 ? message.substring(0, 480) : message;
    }
}
