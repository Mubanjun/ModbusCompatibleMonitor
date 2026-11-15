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

package top.continew.admin.monitor.runtime.sync;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 同步任务的运行时状态（FR-SYNC-06 进度展示与暂停续传）。
 *
 * @author jdrk
 * @since 2026/9/23
 */
public class SyncTaskState {

    /** 任务 ID */
    private final long taskId;
    /** 运行状态（IDLE / RUNNING / PAUSED / DONE / FAILED） */
    private volatile String status = "IDLE";
    /** 暂停请求标记 */
    private final AtomicBoolean pauseRequested = new AtomicBoolean(false);
    /** 当前同步的表 */
    private volatile String currentTable;
    /** 本次运行的总条数（预估） */
    private final AtomicLong total = new AtomicLong(0L);
    /** 成功写入条数 */
    private final AtomicLong success = new AtomicLong(0L);
    /** 失败条数 */
    private final AtomicLong fail = new AtomicLong(0L);
    /** 冲突条数 */
    private final AtomicLong conflict = new AtomicLong(0L);
    /** 已同步（无变化）条数 */
    private final AtomicLong skipped = new AtomicLong(0L);
    /** 最近错误 */
    private volatile String errorMsg;
    /** 开始与结束时间 */
    private volatile long startedAt;
    private volatile long finishedAt;
    /** 本次运行的截止时间（毫秒时间戳；0 表示不限） */
    private volatile long deadline;

    public SyncTaskState(long taskId) {
        this.taskId = taskId;
    }

    /** 任务 ID。 */
    public long taskId() {
        return taskId;
    }

    /** 运行状态。 */
    public String status() {
        return status;
    }

    /**
     * 设置运行状态
     *
     * @param status 状态
     */
    public void status(String status) {
        this.status = status;
    }

    /**
     * 本次运行是否已经结束（DONE / FAILED / PAUSED / 尚未开始）。
     *
     * <p>同步互斥用它判断：RUNNING 表示「已占位（含刚提交、仍在排队）」，其余状态说明上一次已经跑完，
     * 可以开始新一轮。</p>
     *
     * @return 已结束返回 true
     */
    public boolean finished() {
        return !"RUNNING".equals(status) && !"IDLE".equals(status);
    }

    /** 是否请求暂停。 */
    public boolean pauseRequested() {
        return pauseRequested.get();
    }

    /** 请求暂停。 */
    public void requestPause() {
        pauseRequested.set(true);
    }

    /** 清除暂停请求。 */
    public void clearPause() {
        pauseRequested.set(false);
    }

    /** 当前表。 */
    public String currentTable() {
        return currentTable;
    }

    /**
     * 设置当前表
     *
     * @param currentTable 表名
     */
    public void currentTable(String currentTable) {
        this.currentTable = currentTable;
    }

    /** 总条数。 */
    public AtomicLong total() {
        return total;
    }

    /** 成功条数。 */
    public AtomicLong success() {
        return success;
    }

    /** 失败条数。 */
    public AtomicLong fail() {
        return fail;
    }

    /** 冲突条数。 */
    public AtomicLong conflict() {
        return conflict;
    }

    /** 已同步条数。 */
    public AtomicLong skipped() {
        return skipped;
    }

    /** 最近错误。 */
    public String errorMsg() {
        return errorMsg;
    }

    /**
     * 设置最近错误
     *
     * @param errorMsg 错误
     */
    public void errorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    /** 开始时间。 */
    public long startedAt() {
        return startedAt;
    }

    /**
     * 设置开始时间
     *
     * @param startedAt 开始时间
     */
    public void startedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    /** 结束时间。 */
    public long finishedAt() {
        return finishedAt;
    }

    /**
     * 设置结束时间
     *
     * @param finishedAt 结束时间
     */
    public void finishedAt(long finishedAt) {
        this.finishedAt = finishedAt;
    }

    /** 本次运行的截止时间（毫秒时间戳，0 表示不限）。 */
    public long deadline() {
        return deadline;
    }

    /**
     * 设置本次运行的截止时间
     *
     * @param deadline 截止时间（毫秒时间戳，0 表示不限）
     */
    public void deadline(long deadline) {
        this.deadline = deadline;
    }

    /**
     * 是否已超过运行时长上限
     *
     * @return 超过返回 true
     */
    public boolean timedOut() {
        long limit = this.deadline;
        return limit > 0 && System.currentTimeMillis() > limit;
    }
}
