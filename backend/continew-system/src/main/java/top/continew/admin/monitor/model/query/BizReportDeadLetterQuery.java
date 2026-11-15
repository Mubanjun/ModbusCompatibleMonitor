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
import java.time.LocalDateTime;

/**
 * 上报死信查询条件（FR-RPT-05）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "上报死信查询条件")
public class BizReportDeadLetterQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关键词
     */
    @Schema(description = "关键词（目标名称、规则名称或失败原因）")
    @Query(columns = {"target_name", "rule_name", "error_msg"}, type = QueryType.LIKE)
    private String description;

    /**
     * 目标 ID
     */
    @Schema(description = "目标 ID")
    @Query(type = QueryType.EQ)
    private Long targetId;

    /**
     * 规则 ID
     */
    @Schema(description = "规则 ID")
    @Query(type = QueryType.EQ)
    private Long ruleId;

    /**
     * 目标类型
     */
    @Schema(description = "目标类型（MQTT / DB / HTTP）")
    @Query(type = QueryType.EQ)
    private String targetType;

    /**
     * 状态
     */
    @Schema(description = "状态（DEAD / REPLAYED / REPLAY_FAILED）")
    @Query(type = QueryType.EQ)
    private String state;

    /**
     * 起始时间
     */
    @Schema(description = "起始时间（含）")
    @Query(columns = "create_time", type = QueryType.GE)
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间（含）")
    @Query(columns = "create_time", type = QueryType.LE)
    private LocalDateTime endTime;
}
