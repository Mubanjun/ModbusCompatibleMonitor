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
 * 同步冲突响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "同步冲突响应参数")
public class BizSyncConflictResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 同步任务 ID
     */
    @Schema(description = "同步任务 ID", example = "1")
    private Long taskId;

    /**
     * 表名
     */
    @Schema(description = "表名", example = "tbhistory")
    private String tableName;

    /**
     * 记录主键
     */
    @Schema(description = "记录主键", example = "abc123")
    private String recordId;

    /**
     * 来源记录时间
     */
    @Schema(description = "来源记录时间", example = "2026-09-23 10:00:00")
    private LocalDateTime sourceTime;

    /**
     * 目标记录时间
     */
    @Schema(description = "目标记录时间", example = "2026-09-23 09:59:00")
    private LocalDateTime targetTime;

    /**
     * 最终保留的是否为来源数据
     */
    @Schema(description = "最终保留的是否为来源数据", example = "true")
    private Boolean keptSource;

    /**
     * 应用的冲突策略
     */
    @Schema(description = "应用的冲突策略", example = "NEWER_WINS")
    private String policy;

    /**
     * 冲突详情
     */
    @Schema(description = "冲突详情", example = "目标记录较新，保留目标")
    private String detail;
}
