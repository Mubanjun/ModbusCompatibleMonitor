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

/**
 * 新旧判定结论（FR-SYNC-02/05）。
 *
 * @param outcome    同步结论
 * @param reason     判定依据（写入冲突报告的 detail）
 * @param sourceTime 来源记录时间，可为空
 * @param targetTime 目标记录时间，可为空
 */
public record ConflictDecision(SyncOutcome outcome, String reason, Object sourceTime,
    Object targetTime) {

    /** 是否需要写入目标库。 */
    public boolean writable() {
        return outcome.writable();
    }

    /** 是否需要写入冲突报告。 */
    public boolean conflict() {
        return outcome.conflict();
    }
}
