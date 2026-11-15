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
 * 报警记录实体
 *
 * <p>运行时生成数据，仅提供查询。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_alarm_record")
public class BizAlarmRecordDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 命中的规则 ID
     */
    private Long ruleId;

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
     * 节点编号（即通道号）
     */
    private Integer nodeId;

    /**
     * 模拟量序号（1 或 2）
     */
    private Integer analogNo;

    /**
     * 要素名称
     */
    private String elementName;

    /**
     * 报警类型
     */
    private String alarmType;

    /**
     * 报警信息
     */
    private String alarmMessage;

    /**
     * 数据序号
     */
    private Integer dataNo;

    /**
     * 报警数值
     */
    private Double dataValue;

    /**
     * 单位
     */
    private String unit;

    /**
     * 报警范围
     */
    private String alarmRange;

    /**
     * 记录时间
     */
    private LocalDateTime recordTime;

    /**
     * 处理标志（0：未处理；1：已处理；2：已忽略）
     */
    private Integer handlingFlag;

    /**
     * 处理方式
     */
    private String handlingMethod;

    /**
     * 处理人
     */
    private String handlingUser;

    /**
     * 处理时间
     */
    private LocalDateTime handlingTime;

    /**
     * 经度
     */
    private Float lng;

    /**
     * 纬度
     */
    private Float lat;
}
