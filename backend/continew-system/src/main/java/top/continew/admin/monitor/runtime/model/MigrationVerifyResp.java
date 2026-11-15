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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一致性校验报告（FR-MIG-06）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "一致性校验报告")
public class MigrationVerifyResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "校验时间")
    private LocalDateTime checkedAt;

    @Schema(description = "时间范围起点")
    private LocalDateTime startTime;

    @Schema(description = "时间范围终点")
    private LocalDateTime endTime;

    @Schema(description = "源库条数")
    private Long sourceRows;

    @Schema(description = "本系统条数")
    private Long localRows;

    @Schema(description = "抽样条数")
    private Integer sampled;

    @Schema(description = "一致条数")
    private Integer matched;

    @Schema(description = "不一致条数")
    private Integer mismatched;

    @Schema(description = "是否整体一致")
    private Boolean consistent;

    @Schema(description = "结论说明")
    private String conclusion;

    @Schema(description = "抽样明细")
    private List<MigrationSampleResp> samples = new ArrayList<>();
}
