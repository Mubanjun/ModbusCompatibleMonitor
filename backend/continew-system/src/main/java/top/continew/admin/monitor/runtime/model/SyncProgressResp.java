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
 * 同步任务进度（FR-SYNC-06）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "同步任务进度")
public class SyncProgressResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "运行状态（IDLE / RUNNING / PAUSED / DONE / FAILED）")
    private String status;

    @Schema(description = "同步模式（FULL / INCREMENT）")
    private String syncMode;

    @Schema(description = "冲突策略")
    private String conflictPolicy;

    @Schema(description = "当前同步的表")
    private String currentTable;

    @Schema(description = "本次运行总条数（预估）")
    private Long totalCount;

    @Schema(description = "成功写入条数")
    private Long successCount;

    @Schema(description = "冲突条数")
    private Long conflictCount;

    @Schema(description = "已同步（无变化）条数")
    private Long skippedCount;

    @Schema(description = "失败条数")
    private Long failCount;

    @Schema(description = "运行时长（毫秒）")
    private Long durationMs;

    @Schema(description = "最近错误")
    private String errorMsg;
}
