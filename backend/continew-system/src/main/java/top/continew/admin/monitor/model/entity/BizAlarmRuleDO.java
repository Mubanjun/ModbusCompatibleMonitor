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
 * 报警规则实体
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_alarm_rule")
public class BizAlarmRuleDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 设备 ID（空表示按端口/全局）
     */
    private Long deviceId;

    /**
     * 端口 ID
     */
    private Long portId;

    /**
     * 通道号（1~32）
     */
    private Integer channelNo;

    /**
     * 模拟量序号（1 或 2）
     */
    private Integer analogNo;

    /**
     * 要素类型（ElementType 枚举名）
     */
    private String elementType;

    /**
     * 规则类型（RANGE：阈值；DEVIATION：偏差；RATE：变化率；SWITCH：开关量）
     */
    private String ruleType;

    /**
     * 上限
     */
    private BigDecimal upperLimit;

    /**
     * 下限
     */
    private BigDecimal lowerLimit;

    /**
     * 允许偏差
     */
    private BigDecimal deviation;

    /**
     * 变化率上限（单位/分钟）
     */
    private BigDecimal rateLimit;

    /**
     * 开关量报警状态（0：断开；1：闭合）
     */
    private Integer switchState;

    /**
     * 持续超过多久才报警（秒，防抖）
     */
    private Integer durationS;

    /**
     * 抑制窗口（秒）
     */
    private Integer suppressWindowS;

    /**
     * 通知方式（逗号分隔：MQTT,EMAIL,SMS）
     */
    private String notifyMethods;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 描述
     */
    private String description;
}
