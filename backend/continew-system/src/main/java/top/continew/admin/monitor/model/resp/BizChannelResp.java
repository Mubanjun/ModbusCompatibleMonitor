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
import java.math.BigDecimal;

/**
 * 通道配置响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "通道配置响应参数")
public class BizChannelResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属设备 ID
     */
    @Schema(description = "所属设备 ID", example = "1")
    private Long deviceId;

    /**
     * 通道号
     */
    @Schema(description = "通道号（1~32）", example = "1")
    private Integer channelNo;

    /**
     * 模拟量序号
     */
    @Schema(description = "模拟量序号（1 或 2）", example = "2")
    private Integer analogNo;

    /**
     * 水质要素类型
     */
    @Schema(description = "水质要素类型", example = "CONDUCTIVITY")
    private String elementType;

    /**
     * 要素显示名称
     */
    @Schema(description = "要素显示名称", example = "电导率")
    private String elementName;

    /**
     * 单位
     */
    @Schema(description = "单位", example = "us/cm")
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
    @Schema(description = "值编码方式", example = "UINT16_SCALE_1")
    private String encoding;

    /**
     * 寄存器起始地址
     */
    @Schema(description = "寄存器起始地址", example = "0")
    private Integer regAddr;

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
    private Boolean enabled;

    /**
     * 描述
     */
    @Schema(description = "描述")
    private String description;
}
