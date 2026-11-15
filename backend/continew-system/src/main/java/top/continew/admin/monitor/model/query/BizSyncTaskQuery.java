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

package top.continew.admin.monitor.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.continew.starter.data.annotation.Query;
import top.continew.starter.data.enums.QueryType;

import java.io.Serial;
import java.io.Serializable;

/**
 * 同步任务查询条件
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "同步任务查询条件")
public class BizSyncTaskQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关键词
     */
    @Schema(description = "关键词（任务名称或描述）")
    @Query(columns = {"name", "description"}, type = QueryType.LIKE)
    private String description;

    /**
     * 目标库 ID
     */
    @Schema(description = "目标库 ID")
    @Query(type = QueryType.EQ)
    private Long targetId;

    /**
     * 同步模式
     */
    @Schema(description = "同步模式（FULL / INCREMENT）")
    @Query(type = QueryType.EQ)
    private String syncMode;

    /**
     * 任务状态
     */
    @Schema(description = "任务状态（IDLE / RUNNING / PAUSED / DONE / FAILED）")
    @Query(type = QueryType.EQ)
    private String taskStatus;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用")
    @Query(type = QueryType.EQ)
    private Boolean enabled;
}
