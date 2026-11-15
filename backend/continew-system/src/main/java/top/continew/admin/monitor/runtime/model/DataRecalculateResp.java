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
import java.util.ArrayList;
import java.util.List;

/**
 * 历史数据批量补算结果（FR-WEB-08）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "历史数据批量补算结果")
public class DataRecalculateResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否只预演")
    private Boolean dryRun;

    @Schema(description = "扫描条数")
    private Long scanned = 0L;

    @Schema(description = "更新条数")
    private Long updated = 0L;

    @Schema(description = "无需变更条数")
    private Long unchanged = 0L;

    @Schema(description = "跳过条数（无通道配置或无原始值）")
    private Long skipped = 0L;

    @Schema(description = "失败条数")
    private Long failed = 0L;

    @Schema(description = "耗时（毫秒）")
    private Long durationMs;

    @Schema(description = "失败原因（最多 20 条）")
    private List<String> errors = new ArrayList<>();
}
