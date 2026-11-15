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

/**
 * 采集端口响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "采集端口响应参数")
public class BizPortResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 端口名称
     */
    @Schema(description = "端口名称", example = "1 号 485 总线")
    private String name;

    /**
     * 端口类型
     */
    @Schema(description = "端口类型（SERIAL：串口；TCP：串口服务器/Modbus TCP）", example = "SERIAL")
    private String portType;

    /**
     * 端口地址
     */
    @Schema(description = "端口地址（串口号如 COM1，或 IP:端口）", example = "COM1")
    private String portName;

    /**
     * 波特率
     */
    @Schema(description = "波特率", example = "4800")
    private Integer baudRate;

    /**
     * 数据位
     */
    @Schema(description = "数据位", example = "8")
    private Integer dataBits;

    /**
     * 停止位
     */
    @Schema(description = "停止位", example = "1")
    private Integer stopBits;

    /**
     * 校验位
     */
    @Schema(description = "校验位（0：无；1：奇校验；2：偶校验）", example = "0")
    private Integer parity;

    /**
     * 流控
     */
    @Schema(description = "流控（0：无；1：RTS/CTS；2：XON/XOFF）", example = "0")
    private Integer flowControl;

    /**
     * 端口级采集间隔
     */
    @Schema(description = "端口级采集间隔（毫秒，空则继承全局）", example = "5000")
    private Integer intervalMs;

    /**
     * 本端口 485 接入设备数量上限
     */
    @Schema(description = "本端口 485 接入设备数量上限", example = "32")
    private Integer deviceLimit;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    /**
     * 描述
     */
    @Schema(description = "描述", example = "现场 485 总线，接入 8 台水质传感器")
    private String description;

    /**
     * 排序
     */
    @Schema(description = "排序", example = "1")
    private Integer sort;
}
