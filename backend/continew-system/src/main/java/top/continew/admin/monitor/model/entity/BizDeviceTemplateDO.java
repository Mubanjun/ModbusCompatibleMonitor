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

package top.continew.admin.monitor.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.BaseDO;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 设备模板实体
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_device_template")
public class BizDeviceTemplateDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 设备型号
     */
    private String model;

    /**
     * 水质要素类型（ElementType 枚举名）
     */
    private String elementType;

    /**
     * 量程档位说明
     */
    private String rangeLabel;

    /**
     * 单位
     */
    private String unit;

    /**
     * 通道数据属性（ANALOG1_ONLY / ANALOG2_ONLY / BOTH / FLOAT32）
     */
    private String dataType;

    /**
     * 值编码方式（ValueEncoding 枚举名）
     */
    private String encoding;

    /**
     * 系数 A
     */
    private BigDecimal coefficient;

    /**
     * 偏移 B
     */
    private BigDecimal offsetValue;

    /**
     * 量程下限
     */
    private BigDecimal minValue;

    /**
     * 量程上限
     */
    private BigDecimal maxValue;

    /**
     * 是否系统内置模板
     */
    private Boolean isSystem;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 描述
     */
    private String description;

    /**
     * 排序
     */
    private Integer sort;
}
