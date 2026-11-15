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

package top.continew.admin.monitor.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.continew.admin.common.base.model.resp.BaseDetailResp;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 同步任务响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "同步任务响应参数")
public class BizSyncTaskResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务名称
     */
    @Schema(description = "任务名称", example = "历史数据同步至仁科库")
    private String name;

    /**
     * 目标库（上报目标 ID）
     */
    @Schema(description = "目标库（上报目标 ID）", example = "1")
    private Long targetId;

    /**
     * 同步模式
     */
    @Schema(description = "同步模式（FULL / INCREMENT）", example = "INCREMENT")
    private String syncMode;

    /**
     * 冲突策略
     */
    @Schema(description = "冲突策略（NEWER_WINS / OVERWRITE / SKIP / MANUAL）", example = "NEWER_WINS")
    private String conflictPolicy;

    /**
     * 同步的表名
     */
    @Schema(description = "同步的表名（逗号分隔）", example = "tbalarm,tbhistory")
    private String tableNames;

    /**
     * 状态
     */
    @Schema(description = "状态（IDLE / RUNNING / PAUSED / DONE / FAILED）", example = "IDLE")
    private String taskStatus;

    /**
     * 上次同步时间
     */
    @Schema(description = "上次同步时间", example = "2026-09-23 10:00:00", type = "string")
    private LocalDateTime lastSyncTime;

    /**
     * 总记录数
     */
    @Schema(description = "总记录数", example = "1000")
    private Long totalCount;

    /**
     * 成功数
     */
    @Schema(description = "成功数", example = "998")
    private Long successCount;

    /**
     * 失败数
     */
    @Schema(description = "失败数", example = "2")
    private Long failCount;

    /**
     * 最近错误
     */
    @Schema(description = "最近错误")
    private String errorMsg;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    /**
     * 描述
     */
    @Schema(description = "描述")
    private String description;
}
