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

import com.mbj.jdcompatiblemonitor.device.alarm.AlarmEvent;
import com.mbj.jdcompatiblemonitor.device.alarm.AlarmRuleSpec;
import com.mbj.jdcompatiblemonitor.device.alarm.AlarmTracker;
import com.mbj.jdcompatiblemonitor.device.runtime.AcquisitionSample;
import com.mbj.jdcompatiblemonitor.device.runtime.DeviceStatus;
import com.mbj.jdcompatiblemonitor.device.runtime.ReadingSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.service.BizAlarmRecordService;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 报警引擎（FR-ALM-01/02/05/06）。
 *
 * <p>作为 {@code ReadingSink} 消费采集样本：按规则求值 → 防抖/抑制 → 写报警记录 → 触发通知。
 * 规则来自 {@link AlarmRuleCache} 的内存快照，规则内容变化时重建对应状态机并补发恢复事件。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmEngine implements ReadingSink {

    private final AlarmRuleCache alarmRuleCache;
    private final AlarmStateRegistry alarmStateRegistry;
    private final BizAlarmRecordService bizAlarmRecordService;
    private final AlarmNotifyDispatcher alarmNotifyDispatcher;

    private final Map<String, Tracked> trackers = new ConcurrentHashMap<>();
    private final AtomicLong raisedCount = new AtomicLong(0L);
    private final AtomicLong recoveredCount = new AtomicLong(0L);
    private final AtomicLong recordCount = new AtomicLong(0L);

    private volatile int prunedVersion = -1;

    @Override
    public void accept(AcquisitionSample sample) {
        List<AlarmRuleSpec> rules = alarmRuleCache.current();
        this.pruneIfNeeded(rules, sample);
        for (AlarmRuleSpec rule : rules) {
            if (!rule.matches(sample.deviceId(), sample.portId(), sample.channelNo(), sample
                .analogNo())) {
                continue;
            }
            String key = this.key(rule, sample);
            String signature = rule.toString();
            Tracked tracked = trackers.get(key);
            if (tracked == null || !tracked.signature.equals(signature)) {
                if (tracked != null && tracked.tracker.isAlarming()) {
                    // 规则被改动，先为旧规则补一条恢复，避免报警长期悬挂
                    final Tracked previous = tracked;
                    tracked.tracker.reset(sample.collectTime())
                        .ifPresent(event -> this.fire(previous.rule, previous.origin, event));
                }
                tracked = new Tracked(signature, rule, new AlarmTracker(rule), sample);
                trackers.put(key, tracked);
            }
            final Tracked current = tracked;
            current.tracker.onSample(sample.engineeringValue(), sample.unit(), sample.collectTime())
                .ifPresent(event -> this.fire(current.rule, sample, event));
        }
    }

    @Override
    public void onDeviceState(long portId, long deviceId, DeviceStatus status) {
        // 设备离线不主动补发恢复：数据停止上报时保持报警态更符合现场语义
    }

    /**
     * 引擎运行状态
     *
     * @return 状态
     */
    public Map<String, Object> stats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("trackedKeys", trackers.size());
        stats.put("activeAlarms", alarmStateRegistry.activeCount());
        stats.put("raisedCount", raisedCount.get());
        stats.put("recoveredCount", recoveredCount.get());
        stats.put("recordCount", recordCount.get());
        stats.put("ruleVersion", alarmRuleCache.version());
        return stats;
    }

    /**
     * 处理一次报警事件：登记状态、落库、通知
     *
     * @param rule   规则
     * @param sample 采样
     * @param event  事件
     */
    private void fire(AlarmRuleSpec rule, AcquisitionSample sample, AlarmEvent event) {
        if (event.recovered()) {
            alarmStateRegistry.markRecovered(sample.deviceId(), sample.channelNo(), sample
                .analogNo());
            recoveredCount.incrementAndGet();
        } else {
            alarmStateRegistry.markAlarmed(sample.deviceId(), sample.channelNo(), sample.analogNo(),
                event.eventTime());
            raisedCount.incrementAndGet();
        }
        Long recordId = null;
        try {
            recordId = bizAlarmRecordService.record(new AlarmRecordCommand(rule.ruleId(), sample
                .deviceId(), sample.deviceName(), sample.deviceAddr(), sample.channelNo(),
                sample
                    .analogNo(),
                sample.elementName(), event.kind().label(), event.message(), event
                    .value(),
                sample.unit(), event.alarmRange(), event.eventTime(), event
                    .recovered()));
            recordCount.incrementAndGet();
        } catch (Exception e) {
            log.error("写报警记录失败（规则 {}，设备 {}）：{}", rule.ruleId(), sample.deviceId(), e
                .getMessage());
        }
        if (rule.type() == null) {
            return;
        }
        try {
            alarmNotifyDispatcher.dispatch(new AlarmNotifyMessage(recordId, rule.ruleId(), rule
                .name(), null, null, sample.deviceName(), sample.deviceAddr(), sample.channelNo(),
                sample.analogNo(), sample.elementName(), event.kind().label(), event.message(),
                event
                    .alarmRange(),
                event.value(), sample.unit(),
                java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(event.eventTime()),
                    java.time.ZoneId.systemDefault()),
                event.recovered()), this.notifyMethods(rule));
        } catch (Exception e) {
            log.error("报警通知分发失败（规则 {}）：{}", rule.ruleId(), e.getMessage());
        }
    }

    /**
     * 取规则的通知方式
     *
     * @param rule 规则
     * @return 逗号分隔的通知方式，未配置返回 null
     */
    private String notifyMethods(AlarmRuleSpec rule) {
        return alarmRuleCache.notifyMethods(rule.ruleId());
    }

    /**
     * 规则集合变化时清理失效状态机
     *
     * @param rules  当前规则
     * @param sample 触发清理的采样
     */
    private void pruneIfNeeded(List<AlarmRuleSpec> rules, AcquisitionSample sample) {
        int version = alarmRuleCache.version();
        if (version == prunedVersion) {
            return;
        }
        prunedVersion = version;
        Set<Long> alive = new HashSet<>();
        for (AlarmRuleSpec rule : rules) {
            alive.add(rule.ruleId());
        }
        trackers.entrySet().removeIf(entry -> {
            Tracked tracked = entry.getValue();
            if (alive.contains(tracked.rule.ruleId())) {
                return false;
            }
            if (tracked.tracker.isAlarming()) {
                // 必须用状态机自己的来源采样补恢复：prune 是被「任意一条采样」触发的，
                // 用触发采样会把这条恢复记录（以及 AlarmStateRegistry 的状态）记到别的设备/通道上。
                // 时间仍取触发清理的当前时刻（恢复是此刻发现的），只有设备/通道/要素改用来源采样
                tracked.tracker.reset(sample.collectTime())
                    .ifPresent(event -> this.fire(tracked.rule, tracked.origin, event));
            }
            return true;
        });
    }

    private String key(AlarmRuleSpec rule, AcquisitionSample sample) {
        return rule.ruleId() + ":" + sample.deviceId() + ":" + sample.channelNo() + ":" + sample
            .analogNo();
    }

    /**
     * 规则 + 状态机 + 来源采样快照。
     *
     * <p>origin 用于「规则被删除/改动时补恢复事件」：报警记录的设备/通道/要素必须取自状态机自己的
     * 来源采样，而不是恰好触发清理的那一条采样。</p>
     */
    private static final class Tracked {

        private final String signature;
        private final AlarmRuleSpec rule;
        private final AlarmTracker tracker;
        private final AcquisitionSample origin;

        private Tracked(String signature, AlarmRuleSpec rule, AlarmTracker tracker,
            AcquisitionSample origin) {
            this.signature = signature;
            this.rule = rule;
            this.tracker = tracker;
            this.origin = origin;
        }
    }
}
