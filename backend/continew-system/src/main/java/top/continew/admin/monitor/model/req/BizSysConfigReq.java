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
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 业务系统配置创建或修改请求参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "业务系统配置创建或修改请求参数")
public class BizSysConfigReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类
     */
    @Schema(description = "分类（COLLECT：采集；REPORT：上报；STORAGE：存储；SYSTEM：系统）", example = "COLLECT")
    @NotBlank(message = "分类不能为空")
    @Pattern(regexp = "COLLECT|REPORT|STORAGE|SYSTEM",
        message = "分类只能为 COLLECT、REPORT、STORAGE 或 SYSTEM")
    private String category;

    /**
     * 参数名称
     */
    @Schema(description = "参数名称", example = "采集间隔")
    @NotBlank(message = "参数名称不能为空")
    @Length(max = 50, message = "参数名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 参数编码
     */
    @Schema(description = "参数编码（创建后不可修改）", example = "COLLECT_INTERVAL_MS")
    @NotBlank(message = "参数编码不能为空")
    @Length(max = 50, message = "参数编码长度不能超过 {max} 个字符")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,49}$",
        message = "参数编码为 2-50 个字符，支持大小写字母、数字与下划线，以字母开头")
    private String code;

    /**
     * 参数值
     */
    @Schema(description = "参数值", example = "5000")
    @Length(max = 500, message = "参数值长度不能超过 {max} 个字符")
    private String value;

    /**
     * 默认值
     */
    @Schema(description = "默认值", example = "5000")
    @Length(max = 500, message = "默认值长度不能超过 {max} 个字符")
    private String defaultValue;

    /**
     * 说明
     */
    @Schema(description = "说明", example = "全局采集间隔，单位毫秒")
    @Length(max = 200, message = "说明长度不能超过 {max} 个字符")
    private String description;
}
