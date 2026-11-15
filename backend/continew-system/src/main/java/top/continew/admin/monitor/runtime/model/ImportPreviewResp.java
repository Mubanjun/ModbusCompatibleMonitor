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
 * 导入预览（FR-IO-01：导入前校验）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "导入预览")
public class ImportPreviewResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "导入类型")
    private String type;

    @Schema(description = "文件表头")
    private List<String> headers = new ArrayList<>();

    @Schema(description = "必需字段是否齐全")
    private Boolean headersValid;

    @Schema(description = "缺失的必需字段")
    private List<String> missingHeaders = new ArrayList<>();

    @Schema(description = "文件总行数（不含表头）")
    private Integer total;

    @Schema(description = "预览行（最多 20 行）")
    private List<String> sampleRows = new ArrayList<>();
}
