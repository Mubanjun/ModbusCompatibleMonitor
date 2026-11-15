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

package com.mbj.jdcompatiblemonitor.storage;

/**
 * 暂存状态快照（FR-BUF-06：积压条数、最旧数据时间、写入/消费速率）。
 *
 * @param pendingCount      待上传条数
 * @param inFlightCount     已取走但尚未确认的条数
 * @param oldestCollectTime 最旧数据的采集时间（毫秒时间戳，无数据为 0）
 * @param newestCollectTime 最新数据的采集时间（毫秒时间戳，无数据为 0）
 * @param appendedCount     累计写入条数
 * @param flushedCount      累计上传成功条数
 * @param droppedCount      因容量上限被清理的条数
 * @param fileSizeBytes     暂存库文件大小（字节）
 */
public record StagingStats(long pendingCount, long inFlightCount, long oldestCollectTime,
    long newestCollectTime, long appendedCount, long flushedCount, long droppedCount,
    long fileSizeBytes) {

    /** 积压（待上传 + 已取走未确认）总条数。 */
    public long backlog() {
        return pendingCount + inFlightCount;
    }
}
