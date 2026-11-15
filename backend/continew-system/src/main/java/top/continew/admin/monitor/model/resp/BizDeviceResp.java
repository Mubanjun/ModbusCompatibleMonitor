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
 * 采集设备响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "采集设备响应参数")
public class BizDeviceResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属端口 ID
     */
    @Schema(description = "所属端口 ID", example = "1")
    private Long portId;

    /**
     * 设备名称
     */
    @Schema(description = "设备名称", example = "1 号水质传感器")
    private String name;

    /**
     * 从站地址
     */
    @Schema(description = "从站地址（1~255，同一端口内唯一）", example = "1")
    private Integer slaveAddr;

    /**
     * 协议
     */
    @Schema(description = "协议（MODBUS_RTU / RS_MODBUS / MODBUS_TCP）", example = "RS_MODBUS")
    private String protocol;

    /**
     * 设备型号
     */
    @Schema(description = "设备型号", example = "RS-EC-N01-2")
    private String model;

    /**
     * 所属分组 ID
     */
    @Schema(description = "所属分组 ID", example = "1")
    private Long groupId;

    /**
     * 分组（兼容只读）
     */
    @Schema(description = "分组（兼容只读）", example = "进水口")
    private String groupName;

    /**
     * 安装位置
     */
    @Schema(description = "安装位置", example = "污水处理站进水口")
    private String location;

    /**
     * 经度
     */
    @Schema(description = "经度", example = "117.123456")
    private BigDecimal longitude;

    /**
     * 纬度
     */
    @Schema(description = "纬度", example = "36.654321")
    private BigDecimal latitude;

    /**
     * 海拔（米）
     */
    @Schema(description = "海拔（米）", example = "35.20")
    private BigDecimal elevation;

    /**
     * 详细地址
     */
    @Schema(description = "详细地址")
    private String address;

    /**
     * 设备级采集间隔（毫秒）
     */
    @Schema(description = "设备级采集间隔（毫秒，空则继承端口/全局）", example = "5000")
    private Integer intervalMs;

    /**
     * 单帧超时（毫秒）
     */
    @Schema(description = "单帧超时（毫秒）", example = "1000")
    private Integer timeoutMs;

    /**
     * 端口内采集顺序
     */
    @Schema(description = "端口内采集顺序", example = "1")
    private Integer sortNo;

    /**
     * 是否高优先级
     */
    @Schema(description = "是否高优先级（0：普通；1：高优先级，优先调度）", example = "0")
    private Integer priority;

    /**
     * 继电器寄存器起始地址
     */
    @Schema(description = "继电器寄存器起始地址（标准规约 696）", example = "696")
    private Integer relayRegBase;

    /**
     * 继电器路数
     */
    @Schema(description = "继电器路数（标准规约 8 路）", example = "8")
    private Integer relayCount;

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
