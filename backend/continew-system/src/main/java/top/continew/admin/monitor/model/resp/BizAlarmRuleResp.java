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
 * 报警规则响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "报警规则响应参数")
public class BizAlarmRuleResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则名称
     */
    @Schema(description = "规则名称", example = "溶解氧低于 5mg/L")
    private String name;

    /**
     * 规则类型
     */
    @Schema(description = "规则类型（RANGE / DEVIATION / RATE / SWITCH）", example = "RANGE")
    private String ruleType;

    /**
     * 设备 ID
     */
    @Schema(description = "设备 ID", example = "1")
    private Long deviceId;

    /**
     * 端口 ID
     */
    @Schema(description = "端口 ID", example = "1")
    private Long portId;

    /**
     * 通道号
     */
    @Schema(description = "通道号（1~32）", example = "3")
    private Integer channelNo;

    /**
     * 模拟量序号
     */
    @Schema(description = "模拟量序号（1 或 2）", example = "1")
    private Integer analogNo;

    /**
     * 要素类型
     */
    @Schema(description = "水质要素类型", example = "DISSOLVED_OXYGEN")
    private String elementType;

    /**
     * 上限
     */
    @Schema(description = "上限", example = "20")
    private BigDecimal upperLimit;

    /**
     * 下限
     */
    @Schema(description = "下限", example = "5")
    private BigDecimal lowerLimit;

    /**
     * 允许偏差
     */
    @Schema(description = "允许偏差", example = "2")
    private BigDecimal deviation;

    /**
     * 变化率上限
     */
    @Schema(description = "变化率上限（单位/分钟）", example = "3")
    private BigDecimal rateLimit;

    /**
     * 开关量报警状态
     */
    @Schema(description = "开关量报警状态（0：断开；1：闭合）", example = "1")
    private Integer switchState;

    /**
     * 防抖时长（秒）
     */
    @Schema(description = "持续超过多久才报警（秒）", example = "30")
    private Integer durationS;

    /**
     * 抑制窗口（秒）
     */
    @Schema(description = "抑制窗口（秒）", example = "300")
    private Integer suppressWindowS;

    /**
     * 通知方式
     */
    @Schema(description = "通知方式（逗号分隔）", example = "MQTT,EMAIL")
    private String notifyMethods;

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
