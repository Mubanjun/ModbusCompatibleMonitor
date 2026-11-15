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
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 通道配置创建或修改请求参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "通道配置创建或修改请求参数")
public class BizChannelReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属设备 ID
     */
    @Schema(description = "所属设备 ID", example = "1")
    @NotNull(message = "所属设备不能为空")
    private Long deviceId;

    /**
     * 通道号
     */
    @Schema(description = "通道号（1~32，对应主机通道）", example = "1")
    @NotNull(message = "通道号不能为空")
    @Min(value = 1, message = "通道号范围为 1~32")
    @Max(value = 32, message = "通道号范围为 1~32")
    private Integer channelNo;

    /**
     * 模拟量序号
     */
    @Schema(description = "模拟量序号（1 或 2）", example = "2")
    @NotNull(message = "模拟量序号不能为空")
    @Min(value = 1, message = "模拟量序号只能为 1 或 2")
    @Max(value = 2, message = "模拟量序号只能为 1 或 2")
    private Integer analogNo;

    /**
     * 水质要素类型
     */
    @Schema(description = "水质要素类型（ElementType 枚举名，如 CONDUCTIVITY）", example = "CONDUCTIVITY")
    @Length(max = 40, message = "要素类型长度不能超过 {max} 个字符")
    private String elementType;

    /**
     * 要素量程档位（仅用于套用模板，不落库）
     */
    @Schema(description = "要素量程档位说明（仅用于套用模板：填写后自动带出编码方式、系数 A、单位与量程，不落库）",
        example = "量程 10~20000 实际值")
    private String rangeLabel;

    /**
     * 要素显示名称
     */
    @Schema(description = "要素显示名称（不填则按要素类型自动带出）", example = "电导率")
    @Length(max = 50, message = "要素名称长度不能超过 {max} 个字符")
    private String elementName;

    /**
     * 单位
     */
    @Schema(description = "单位（不填则按要素类型自动带出）", example = "us/cm")
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
     * 寄存器起始地址
     */
    @Schema(description = "寄存器起始地址（0 = 模拟量2，1 = 模拟量1）", example = "0")
    @Min(value = 0, message = "寄存器地址不能小于 {value}")
    @Max(value = 65535, message = "寄存器地址不能大于 {value}")
    private Integer regAddr;

    /**
     * 系数 A
     */
    @Schema(description = "系数 A（工程值 = 原始值 × A + B，不填则按量程档位带出）", example = "1")
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
}
