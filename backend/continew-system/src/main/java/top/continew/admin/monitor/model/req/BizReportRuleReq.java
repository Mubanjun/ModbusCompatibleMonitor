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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 上报规则创建或修改请求参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "上报规则创建或修改请求参数")
public class BizReportRuleReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则名称
     */
    @Schema(description = "规则名称", example = "进水口数据上报")
    @NotBlank(message = "规则名称不能为空")
    @Length(max = 50, message = "规则名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 上报目标 ID
     */
    @Schema(description = "上报目标 ID", example = "1")
    @NotNull(message = "上报目标不能为空")
    private Long targetId;

    /**
     * 上报模板 ID
     */
    @Schema(description = "上报模板 ID（留空则使用目标默认格式）", example = "1")
    private Long templateId;

    /**
     * 限定端口 ID
     */
    @Schema(description = "限定端口 ID（留空表示全部端口）", example = "1")
    private Long portId;

    /**
     * 限定设备 ID
     */
    @Schema(description = "限定设备 ID（留空表示全部设备）", example = "1")
    private Long deviceId;

    /**
     * 上报间隔
     */
    @Schema(description = "上报间隔（毫秒，默认 60 秒）", example = "60000")
    @Min(value = 1000, message = "上报间隔不能小于 {value} 毫秒")
    @Max(value = 86400000, message = "上报间隔不能大于 {value} 毫秒")
    private Integer intervalMs;

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
