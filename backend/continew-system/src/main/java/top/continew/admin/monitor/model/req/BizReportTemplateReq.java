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

package top.continew.admin.monitor.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 上报模板创建或修改请求参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "上报模板创建或修改请求参数")
public class BizReportTemplateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板名称
     */
    @Schema(description = "模板名称", example = "标准水质 JSON 报文")
    @NotBlank(message = "模板名称不能为空")
    @Length(max = 50, message = "模板名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 模板编码
     */
    @Schema(description = "模板编码", example = "water_quality_json")
    @NotBlank(message = "模板编码不能为空")
    @Length(max = 50, message = "模板编码长度不能超过 {max} 个字符")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,49}$",
        message = "模板编码为 2-50 个字符，支持大小写字母、数字与下划线，以字母开头")
    private String code;

    /**
     * 报文格式
     */
    @Schema(description = "报文格式（JSON / KV / CUSTOM）", example = "JSON")
    @NotBlank(message = "报文格式不能为空")
    @Pattern(regexp = "JSON|KV|CUSTOM", message = "报文格式只能为 JSON、KV 或 CUSTOM")
    private String formatType;

    /**
     * 模板内容
     */
    @Schema(description = "模板内容，支持变量占位",
        example = "{\"device\":\"${deviceName}\",\"time\":\"${recordTime}\",\"value\":${engValue}}")
    @NotBlank(message = "模板内容不能为空")
    @Length(max = 10000, message = "模板内容长度不能超过 {max} 个字符")
    private String content;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用", example = "true")
    @NotNull(message = "是否启用不能为空")
    private Boolean enabled;

    /**
     * 描述
     */
    @Schema(description = "描述")
    @Length(max = 200, message = "描述长度不能超过 {max} 个字符")
    private String description;
}
