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
 * 导入结果报告（FR-IO-01）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "导入结果报告")
public class ImportReportResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "导入类型")
    private String type;

    @Schema(description = "是否仅校验（试运行）")
    private Boolean dryRun;

    @Schema(description = "文件行数（不含表头）")
    private Integer total;

    @Schema(description = "成功条数")
    private Integer success;

    @Schema(description = "跳过条数（已存在）")
    private Integer skipped;

    @Schema(description = "失败条数")
    private Integer failed;

    @Schema(description = "处理耗时（毫秒）")
    private Long durationMs;

    @Schema(description = "错误明细（最多保留 200 条）")
    private List<ImportRowErrorResp> errors = new ArrayList<>();
}
