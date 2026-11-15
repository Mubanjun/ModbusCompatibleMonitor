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

package top.continew.admin.monitor.runtime.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 本地暂存与落地状态（FR-BUF-06：积压条数、最旧数据时间、写入/消费速率）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "本地暂存与落地状态")
public class StagingStatusResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "待上传条数")
    private Long pendingCount;

    @Schema(description = "上传中条数")
    private Long inFlightCount;

    @Schema(description = "积压总数")
    private Long backlog;

    @Schema(description = "最旧数据采集时间（毫秒时间戳）")
    private Long oldestCollectTime;

    @Schema(description = "最新数据采集时间（毫秒时间戳）")
    private Long newestCollectTime;

    @Schema(description = "累计写入暂存条数")
    private Long appendedCount;

    @Schema(description = "累计上传成功条数")
    private Long flushedCount;

    @Schema(description = "因容量上限被清理条数")
    private Long droppedCount;

    @Schema(description = "暂存库文件大小（字节）")
    private Long fileSizeBytes;

    @Schema(description = "内存中待写入暂存的条数")
    private Long queuedCount;

    @Schema(description = "因背压被丢弃的内存样本条数")
    private Long queuedDroppedCount;

    @Schema(description = "上传成功批次")
    private Long uploadedBatches;

    @Schema(description = "上传成功条数")
    private Long uploadedRows;

    @Schema(description = "上传失败批次")
    private Long failedBatches;

    @Schema(description = "已清理的历史数据条数")
    private Long purgedHistoryRows;

    @Schema(description = "暂存容量上限（行数）")
    private Long bufferMaxRows;

    @Schema(description = "历史数据保留天数")
    private Integer retainDays;

    @Schema(description = "落地周期（毫秒）")
    private Integer flushIntervalMs;

    @Schema(description = "单批上传条数")
    private Integer batchSize;

    @Schema(description = "暂存库路径")
    private String databasePath;

    @Schema(description = "最近一次失败原因")
    private String lastError;

    @Schema(description = "最近一次失败时间（毫秒时间戳）")
    private Long lastErrorTime;

    @Schema(description = "暂存库所在分区剩余空间（MB，NFR-FAULT-03）")
    private Long freeSpaceMb;

    @Schema(description = "磁盘剩余空间告警阈值（MB）")
    private Long minFreeMb;

    @Schema(description = "磁盘剩余空间保护阈值（MB）")
    private Long protectFreeMb;

    @Schema(description = "磁盘空间级别（OK/WARN/PROTECT）")
    private String diskLevel;

    @Schema(description = "是否处于磁盘保护模式（已收紧本地暂存上限并尝试落地）")
    private Boolean protectionMode;
}
