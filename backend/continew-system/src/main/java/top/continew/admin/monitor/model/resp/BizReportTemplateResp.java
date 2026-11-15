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

/**
 * 上报模板响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "上报模板响应参数")
public class BizReportTemplateResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板名称
     */
    @Schema(description = "模板名称", example = "标准水质 JSON 报文")
    private String name;

    /**
     * 模板编码
     */
    @Schema(description = "模板编码", example = "water_quality_json")
    private String code;

    /**
     * 报文格式
     */
    @Schema(description = "报文格式（JSON / KV / CUSTOM）", example = "JSON")
    private String formatType;

    /**
     * 模板内容，支持变量占位
     */
    @Schema(description = "模板内容，支持变量占位")
    private String content;

    /**
     * 是否系统内置模板
     */
    @Schema(description = "是否系统内置模板", example = "false")
    private Boolean isSystem;

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
