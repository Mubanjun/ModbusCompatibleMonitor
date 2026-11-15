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
import java.time.LocalDateTime;

/**
 * 采集历史数据实体（FR-WEB-05，字段语义对齐仁科 {@code tbhistory}）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_data")
public class BizDataDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 设备 ID
     */
    private Long deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备地址
     */
    private Integer deviceAddr;

    /**
     * 节点编号（通道号）
     */
    private Integer nodeId;

    /**
     * 模拟量序号
     */
    private Integer analogNo;

    /**
     * 要素类型
     */
    private String elementType;

    /**
     * 要素名称
     */
    private String elementName;

    /**
     * 工程值
     */
    private Double dataValue;

    /**
     * 原始值
     */
    private Double rawValue;

    /**
     * 单位
     */
    private String unit;

    /**
     * 是否报警（0：否；1：是）
     */
    private Integer alarmFlag;

    /**
     * 记录时间
     */
    private LocalDateTime recordTime;

    /**
     * 来源暂存记录 ID（幂等去重）
     */
    private Long sourceId;
}
