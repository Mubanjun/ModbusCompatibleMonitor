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
import java.math.BigDecimal;

/**
 * 设备模板创建或修改请求参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "设备模板创建或修改请求参数")
public class BizDeviceTemplateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板名称
     */
    @Schema(description = "模板名称", example = "电导率 RS-EC-N01-2 10~20000")
    @NotBlank(message = "模板名称不能为空")
    @Length(max = 50, message = "模板名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 设备型号
     */
    @Schema(description = "设备型号", example = "RS-EC-N01-2")
    @Length(max = 50, message = "设备型号长度不能超过 {max} 个字符")
    private String model;

    /**
     * 水质要素类型
     */
    @Schema(description = "水质要素类型（ElementType 枚举名）", example = "CONDUCTIVITY")
    @Length(max = 40, message = "要素类型长度不能超过 {max} 个字符")
    private String elementType;

    /**
     * 量程档位说明
     */
    @Schema(description = "量程档位说明（填写后自动带出编码方式、系数 A、单位与量程）",
        example = "量程 10~20000 实际值")
    private String rangeLabel;

    /**
     * 单位
     */
    @Schema(description = "单位", example = "us/cm")
    @Length(max = 20, message = "单位长度不能超过 {max} 个字符")
    private String unit;

    /**
     * 通道数据属性
     */
    @Schema(description = "通道数据属性（ANALOG1_ONLY / ANALOG2_ONLY / BOTH / FLOAT32）",
        example = "ANALOG2_ONLY")
    private String dataType;

    /**
     * 值编码方式
     */
    @Schema(description = "值编码方式（ValueEncoding 枚举名）", example = "UINT16_SCALE_1")
    @Length(max = 30, message = "编码方式长度不能超过 {max} 个字符")
    private String encoding;

    /**
     * 系数 A
     */
    @Schema(description = "系数 A（工程值 = 原始值 × A + B）", example = "1")
    private BigDecimal coefficient;

    /**
     * 偏移 B
     */
    @Schema(description = "偏移 B", example = "0")
    private BigDecimal offsetValue;

    /**
     * 量程下限
     */
    @Schema(description = "量程下限")
    private BigDecimal minValue;

    /**
     * 量程上限
     */
    @Schema(description = "量程上限")
    private BigDecimal maxValue;

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

    /**
     * 排序
     */
    @Schema(description = "排序", example = "1")
    @Min(value = 0, message = "排序不能小于 {value}")
    @Max(value = 999, message = "排序不能大于 {value}")
    private Integer sort;
}
