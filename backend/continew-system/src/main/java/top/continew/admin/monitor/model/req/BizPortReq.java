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

/**
 * 采集端口创建或修改请求参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "采集端口创建或修改请求参数")
public class BizPortReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 端口名称
     */
    @Schema(description = "端口名称", example = "1 号 485 总线")
    @NotBlank(message = "端口名称不能为空")
    @Length(max = 50, message = "端口名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 端口类型
     */
    @Schema(description = "端口类型（SERIAL：串口；TCP：Modbus TCP；RFC2217：RFC2217 串口服务器）",
        example = "SERIAL")
    @NotBlank(message = "端口类型不能为空")
    @Pattern(regexp = "SERIAL|TCP|RFC2217", message = "端口类型只能为 SERIAL、TCP 或 RFC2217")
    private String portType;

    /**
     * 端口地址
     */
    @Schema(description = "端口地址（串口号如 COM1，或 IP:端口）", example = "COM1")
    @NotBlank(message = "端口地址不能为空")
    @Length(max = 100, message = "端口地址长度不能超过 {max} 个字符")
    private String portName;

    /**
     * 波特率
     */
    @Schema(description = "波特率（TCP 端口可空，仁科设备默认 4800）", example = "4800")
    @Min(value = 0, message = "波特率不能小于 {value}")
    @Max(value = 4000000, message = "波特率不能大于 {value}")
    private Integer baudRate;

    /**
     * 数据位
     */
    @Schema(description = "数据位", example = "8")
    @Min(value = 5, message = "数据位只能为 5~8")
    @Max(value = 8, message = "数据位只能为 5~8")
    private Integer dataBits;

    /**
     * 停止位
     */
    @Schema(description = "停止位", example = "1")
    @Min(value = 1, message = "停止位只能为 1 或 2")
    @Max(value = 2, message = "停止位只能为 1 或 2")
    private Integer stopBits;

    /**
     * 校验位
     */
    @Schema(description = "校验位（0：无；1：奇校验；2：偶校验）", example = "0")
    @Min(value = 0, message = "校验位只能为 0~2")
    @Max(value = 2, message = "校验位只能为 0~2")
    private Integer parity;

    /**
     * 流控
     */
    @Schema(description = "流控（0：无；1：RTS/CTS；2：XON/XOFF）", example = "0")
    @Min(value = 0, message = "流控只能为 0~2")
    @Max(value = 2, message = "流控只能为 0~2")
    private Integer flowControl;

    /**
     * 端口级采集间隔
     */
    @Schema(description = "端口级采集间隔（毫秒，空则继承全局）", example = "5000")
    @Min(value = 1000, message = "采集间隔不能小于 {value} 毫秒")
    @Max(value = 86400000, message = "采集间隔不能大于 {value} 毫秒")
    private Integer intervalMs;

    /**
     * 本端口 485 接入设备数量上限
     */
    @Schema(description = "本端口 485 接入设备数量上限（默认 32，可配至 256）", example = "32")
    @NotNull(message = "设备数量上限不能为空")
    @Min(value = 1, message = "设备数量上限不能小于 {value}")
    @Max(value = 256, message = "设备数量上限不能大于 {value}")
    private Integer deviceLimit;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用", example = "true")
    @NotNull(message = "是否启用不能为空")
    private Boolean enabled;

    /**
     * 描述
     */
    @Schema(description = "描述", example = "现场 485 总线，接入 8 台水质传感器")
    @Length(max = 200, message = "描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 排序
     */
    @Schema(description = "排序", example = "1")
    @Min(value = 0, message = "排序不能小于 {value}")
    @Max(value = 999, message = "排序不能大于 {value}")
    private Integer sort;
}
