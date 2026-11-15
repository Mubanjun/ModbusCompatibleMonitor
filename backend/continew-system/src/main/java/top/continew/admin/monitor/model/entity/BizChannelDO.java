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
 * 通道与模拟量配置实体
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_channel")
public class BizChannelDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属设备 ID
     */
    private Long deviceId;

    /**
     * 通道号（1~32）
     */
    private Integer channelNo;

    /**
     * 模拟量序号（1 或 2）
     */
    private Integer analogNo;

    /**
     * 水质要素类型（ElementType 枚举名）
     */
    private String elementType;

    /**
     * 要素显示名称
     */
    private String elementName;

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
     * 寄存器起始地址
     */
    private Integer regAddr;

    /**
     * 系数 A（工程值 = 原始值 × A + B）
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
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 描述
     */
    private String description;
}
