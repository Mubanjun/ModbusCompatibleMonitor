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
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 报警规则创建或修改请求参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "报警规则创建或修改请求参数")
public class BizAlarmRuleReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则名称
     */
    @Schema(description = "规则名称", example = "溶解氧低于 5mg/L")
    @NotBlank(message = "规则名称不能为空")
    @Length(max = 50, message = "规则名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 规则类型
     */
    @Schema(description = "规则类型（RANGE：阈值；DEVIATION：偏差；RATE：变化率；SWITCH：开关量）", example = "RANGE")
    @NotBlank(message = "规则类型不能为空")
    @Pattern(regexp = "RANGE|DEVIATION|RATE|SWITCH",
        message = "规则类型只能为 RANGE、DEVIATION、RATE 或 SWITCH")
    private String ruleType;

    /**
     * 设备 ID
     */
    @Schema(description = "设备 ID（留空表示按端口或全局）", example = "1")
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
    @Min(value = 1, message = "通道号范围为 1~32")
    @Max(value = 32, message = "通道号范围为 1~32")
    private Integer channelNo;

    /**
     * 模拟量序号
     */
    @Schema(description = "模拟量序号（1 或 2）", example = "1")
    @Min(value = 1, message = "模拟量序号只能为 1 或 2")
    @Max(value = 2, message = "模拟量序号只能为 1 或 2")
    private Integer analogNo;

    /**
     * 要素类型
     */
    @Schema(description = "水质要素类型（ElementType 枚举名）", example = "DISSOLVED_OXYGEN")
    @Length(max = 40, message = "要素类型长度不能超过 {max} 个字符")
    private String elementType;

    /**
     * 上限
     */
    @Schema(description = "上限（RANGE 规则使用）", example = "20")
    private BigDecimal upperLimit;

    /**
     * 下限
     */
    @Schema(description = "下限（RANGE 规则使用）", example = "5")
    private BigDecimal lowerLimit;

    /**
     * 允许偏差
     */
    @Schema(description = "允许偏差（DEVIATION 规则使用）", example = "2")
    private BigDecimal deviation;

    /**
     * 变化率上限
     */
    @Schema(description = "变化率上限（RATE 规则使用，单位/分钟）", example = "3")
    private BigDecimal rateLimit;

    /**
     * 开关量报警状态
     */
    @Schema(description = "开关量报警状态（SWITCH 规则使用，0：断开；1：闭合）", example = "1")
    @Min(value = 0, message = "开关量状态只能为 0 或 1")
    @Max(value = 1, message = "开关量状态只能为 0 或 1")
    private Integer switchState;

    /**
     * 防抖时长
     */
    @Schema(description = "持续超过多久才报警（秒，0 表示立即报警）", example = "30")
    @Min(value = 0, message = "防抖时长不能小于 {value}")
    @Max(value = 86400, message = "防抖时长不能大于 {value}")
    private Integer durationS;

    /**
     * 抑制窗口
     */
    @Schema(description = "抑制窗口（秒，窗口内同类报警不重复产生）", example = "300")
    @Min(value = 0, message = "抑制窗口不能小于 {value}")
    @Max(value = 86400, message = "抑制窗口不能大于 {value}")
    private Integer suppressWindowS;

    /**
     * 通知方式
     */
    @Schema(description = "通知方式（逗号分隔：MQTT,EMAIL,SMS）", example = "MQTT,EMAIL")
    @Length(max = 100, message = "通知方式长度不能超过 {max} 个字符")
    private String notifyMethods;

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
