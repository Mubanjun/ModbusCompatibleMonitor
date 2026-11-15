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

/** 一条记录的同步结论（FR-SYNC-02/05）。 */
public enum SyncOutcome {

    /** 目标不存在，新增 */
    INSERTED("新增"),

    /** 以来源数据更新目标 */
    UPDATED("更新"),

    /** 保留目标数据（来源被判定为旧或策略要求跳过） */
    KEPT_TARGET("保留目标"),

    /** 记录时间相同，视为已同步（重复同步不产生冲突） */
    ALREADY_SYNCED("已同步"),

    /** 冲突挂起，等待人工处理 */
    PENDING_MANUAL("待人工确认");

    private final String label;

    SyncOutcome(String label) {
        this.label = label;
    }

    /** 中文名称。 */
    public String label() {
        return label;
    }

    /** 是否需要写入冲突报告。 */
    public boolean conflict() {
        return this == KEPT_TARGET || this == PENDING_MANUAL;
    }

    /** 是否需要写入目标库。 */
    public boolean writable() {
        return this == INSERTED || this == UPDATED;
    }
}
