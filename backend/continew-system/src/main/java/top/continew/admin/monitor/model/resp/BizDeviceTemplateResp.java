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
 * 设备模板响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "设备模板响应参数")
public class BizDeviceTemplateResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板名称
     */
    @Schema(description = "模板名称", example = "电导率 RS-EC-N01-2 10~20000")
    private String name;

    /**
     * 设备型号
     */
    @Schema(description = "设备型号", example = "RS-EC-N01-2")
    private String model;

    /**
     * 水质要素类型
     */
    @Schema(description = "水质要素类型", example = "CONDUCTIVITY")
    private String elementType;

    /**
     * 量程档位说明
     */
    @Schema(description = "量程档位说明", example = "量程 10~20000 实际值")
    private String rangeLabel;

    /**
     * 单位
     */
    @Schema(description = "单位", example = "us/cm")
    private String unit;

    /**
     * 通道数据属性
     */
    @Schema(description = "通道数据属性", example = "ANALOG2_ONLY")
    private String dataType;

    /**
     * 值编码方式
     */
    @Schema(description = "值编码方式", example = "UINT16_SCALE_1")
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

    /**
     * 排序
     */
    @Schema(description = "排序", example = "1")
    private Integer sort;
}
