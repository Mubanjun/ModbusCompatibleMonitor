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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
 * 采集设备创建或修改请求参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "采集设备创建或修改请求参数")
public class BizDeviceReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属端口 ID
     */
    @Schema(description = "所属端口 ID", example = "1")
    @NotNull(message = "所属端口不能为空")
    private Long portId;

    /**
     * 设备名称
     */
    @Schema(description = "设备名称", example = "1 号水质传感器")
    @NotBlank(message = "设备名称不能为空")
    @Length(max = 50, message = "设备名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 从站地址
     */
    @Schema(description = "从站地址（1~255，同一端口内唯一）", example = "1")
    @NotNull(message = "从站地址不能为空")
    @Min(value = 1, message = "从站地址范围为 1~255")
    @Max(value = 255, message = "从站地址范围为 1~255")
    private Integer slaveAddr;

    /**
     * 协议
     */
    @Schema(description = "协议（MODBUS_RTU / RS_MODBUS / MODBUS_TCP / MODBUS_ASCII）",
        example = "RS_MODBUS")
    @Pattern(regexp = "MODBUS_RTU|RS_MODBUS|MODBUS_TCP|MODBUS_ASCII",
        message = "协议只能为 MODBUS_RTU、RS_MODBUS、MODBUS_TCP 或 MODBUS_ASCII")
    private String protocol;

    /**
     * 设备型号
     */
    @Schema(description = "设备型号", example = "RS-EC-N01-2")
    @Length(max = 50, message = "设备型号长度不能超过 {max} 个字符")
    private String model;

    /**
     * 所属分组 ID
     */
    @Schema(description = "所属分组 ID（空则未分组）", example = "1")
    private Long groupId;

    /**
     * 分组（兼容只读）
     */
    @Schema(description = "分组（兼容只读）", example = "进水口")
    @Length(max = 50, message = "分组长度不能超过 {max} 个字符")
    private String groupName;

    /**
     * 安装位置
     */
    @Schema(description = "安装位置", example = "污水处理站进水口")
    @Length(max = 200, message = "安装位置长度不能超过 {max} 个字符")
    private String location;

    /**
     * 经度
     */
    @Schema(description = "经度", example = "117.123456")
    @DecimalMin(value = "-180", message = "经度范围为 -180~180")
    @DecimalMax(value = "180", message = "经度范围为 -180~180")
    private BigDecimal longitude;

    /**
     * 纬度
     */
    @Schema(description = "纬度", example = "36.654321")
    @DecimalMin(value = "-90", message = "纬度范围为 -90~90")
    @DecimalMax(value = "90", message = "纬度范围为 -90~90")
    private BigDecimal latitude;

    /**
     * 海拔（米）
     */
    @Schema(description = "海拔（米）", example = "35.20")
    @DecimalMin(value = "-500", message = "海拔超出合理范围")
    @DecimalMax(value = "9000", message = "海拔超出合理范围")
    private BigDecimal elevation;

    /**
     * 详细地址
     */
    @Schema(description = "详细地址")
    @Length(max = 255, message = "地址长度不能超过 {max} 个字符")
    private String address;

    /**
     * 设备级采集间隔
     */
    @Schema(description = "设备级采集间隔（毫秒，空则继承端口/全局）", example = "5000")
    @Min(value = 1000, message = "采集间隔不能小于 {value} 毫秒")
    @Max(value = 86400000, message = "采集间隔不能大于 {value} 毫秒")
    private Integer intervalMs;

    /**
     * 单帧超时
     */
    @Schema(description = "单帧超时（毫秒）", example = "1000")
    @Min(value = 100, message = "超时不能小于 {value} 毫秒")
    @Max(value = 60000, message = "超时不能大于 {value} 毫秒")
    private Integer timeoutMs;

    /**
     * 端口内采集顺序
     */
    @Schema(description = "端口内采集顺序", example = "1")
    @Min(value = 0, message = "采集顺序不能小于 {value}")
    @Max(value = 9999, message = "采集顺序不能大于 {value}")
    private Integer sortNo;

    /**
     * 是否高优先级
     */
    @Schema(description = "是否高优先级（0：普通；1：高优先级，优先调度）", example = "0")
    @Min(value = 0, message = "优先级只能为 0 或 1")
    @Max(value = 1, message = "优先级只能为 0 或 1")
    private Integer priority;

    /**
     * 继电器寄存器起始地址
     */
    @Schema(description = "继电器寄存器起始地址（标准规约 696）", example = "696")
    @Min(value = 0, message = "寄存器地址不能小于 {value}")
    @Max(value = 65535, message = "寄存器地址不能大于 {value}")
    private Integer relayRegBase;

    /**
     * 继电器路数
     */
    @Schema(description = "继电器路数（标准规约 8 路）", example = "8")
    @Min(value = 1, message = "继电器路数范围为 1~32")
    @Max(value = 32, message = "继电器路数范围为 1~32")
    private Integer relayCount;

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
