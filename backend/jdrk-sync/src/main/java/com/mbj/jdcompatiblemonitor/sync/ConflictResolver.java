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

package com.mbj.jdcompatiblemonitor.sync;

import java.time.LocalDateTime;

/**
 * 新旧判定与冲突策略（FR-SYNC-02）。
 *
 * <p>判定依据为记录时间 {@code RecordTime}：</p>
 * <ul>
 *   <li>{@code NEWER_WINS}（默认）：来源更新则更新目标，否则保留目标并记冲突；时间相同视为已同步；</li>
 *   <li>{@code OVERWRITE}：一律以来源为准；</li>
 *   <li>{@code SKIP}：目标已存在即保留目标并记冲突；</li>
 *   <li>{@code MANUAL}：目标已存在即挂起并记冲突，等待人工处理。</li>
 * </ul>
 */
public final class ConflictResolver {

    private ConflictResolver() {
    }

    /**
     * 判定一条记录如何同步
     *
     * @param policy 冲突策略
     * @param source 来源记录
     * @param target 目标库已有记录，不存在传 {@code null}
     * @return 判定结论
     */
    public static ConflictDecision resolve(ConflictPolicy policy, SyncRecord source,
        SyncRecord target) {
        ConflictPolicy effective = policy == null ? ConflictPolicy.NEWER_WINS : policy;
        LocalDateTime sourceTime = source == null ? null : source.recordTime();
        Object targetTime = target == null ? null : target.recordTime();
        if (source == null) {
            return new ConflictDecision(SyncOutcome.PENDING_MANUAL, "来源记录缺失", null, targetTime);
        }
        if (target == null) {
            return new ConflictDecision(SyncOutcome.INSERTED, "目标库不存在该记录，直接写入",
                sourceTime, null);
        }
        return switch (effective) {
            case OVERWRITE -> new ConflictDecision(SyncOutcome.UPDATED,
                "策略为" + effective.label() + "，以来源数据为准", sourceTime, targetTime);
            case SKIP -> new ConflictDecision(SyncOutcome.KEPT_TARGET,
                "策略为" + effective.label() + "，目标已存在，保留目标数据", sourceTime, targetTime);
            case MANUAL -> new ConflictDecision(SyncOutcome.PENDING_MANUAL,
                "策略为" + effective.label() + "，冲突挂起等待人工处理", sourceTime, targetTime);
            case NEWER_WINS ->
                resolveByTime(sourceTime, target.recordTime(), sourceTime, targetTime);
        };
    }

    private static ConflictDecision resolveByTime(LocalDateTime sourceTime,
        LocalDateTime targetTime, Object sourceTimeValue, Object targetTimeValue) {
        if (sourceTime == null || targetTime == null) {
            // 时间缺失时无法判定新旧，以来源为准并说明依据
            return new ConflictDecision(SyncOutcome.UPDATED, "记录时间缺失，以来源数据为准",
                sourceTimeValue, targetTimeValue);
        }
        int compared = sourceTime.compareTo(targetTime);
        if (compared > 0) {
            return new ConflictDecision(SyncOutcome.UPDATED, "来源较新（" + sourceTime + " > "
                + targetTime + "）", sourceTimeValue, targetTimeValue);
        }
        if (compared < 0) {
            return new ConflictDecision(SyncOutcome.KEPT_TARGET, "目标较新（" + targetTime + " > "
                + sourceTime + "），保留目标数据", sourceTimeValue, targetTimeValue);
        }
        return new ConflictDecision(SyncOutcome.ALREADY_SYNCED, "记录时间相同（" + sourceTime
            + "），视为已同步", sourceTimeValue, targetTimeValue);
    }
}
