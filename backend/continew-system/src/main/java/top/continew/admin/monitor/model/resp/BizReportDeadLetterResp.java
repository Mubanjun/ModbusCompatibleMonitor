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
 * 上报死信响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "上报死信响应参数")
public class BizReportDeadLetterResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "目标 ID")
    private Long targetId;

    @Schema(description = "规则 ID")
    private Long ruleId;

    @Schema(description = "目标名称")
    private String targetName;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "目标类型")
    private String targetType;

    @Schema(description = "报文格式")
    private String formatType;

    @Schema(description = "失败时的报文内容")
    private String payload;

    @Schema(description = "数据条数")
    private Integer itemCount;

    @Schema(description = "已重试次数")
    private Integer retryCount;

    @Schema(description = "状态", example = "DEAD")
    private String state;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "首次失败时间")
    private LocalDateTime firstFailTime;

    @Schema(description = "最近重放时间")
    private LocalDateTime replayTime;

    @Schema(description = "重放结果说明")
    private String replayResult;
}
