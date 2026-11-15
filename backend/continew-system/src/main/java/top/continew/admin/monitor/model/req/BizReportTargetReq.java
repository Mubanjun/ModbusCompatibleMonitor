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
 * 上报目标创建或修改请求参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "上报目标创建或修改请求参数")
public class BizReportTargetReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标名称
     */
    @Schema(description = "目标名称", example = "厂区 MQTT Broker")
    @NotBlank(message = "目标名称不能为空")
    @Length(max = 50, message = "目标名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 目标类型
     */
    @Schema(description = "目标类型（MQTT / DB / HTTP）", example = "MQTT")
    @NotBlank(message = "目标类型不能为空")
    @Pattern(regexp = "MQTT|DB|HTTP", message = "目标类型只能为 MQTT、DB 或 HTTP")
    private String targetType;

    /**
     * 数据库方言
     */
    @Schema(description = "数据库方言（目标类型为 DB 时必填）",
        example = "POSTGRESQL")
    @Length(max = 30, message = "数据库方言长度不能超过 {max} 个字符")
    private String dbType;

    /**
     * 连接参数
     */
    @Schema(description = "连接参数（JSON：host、port、topic、username、password、database 等）",
        example = "{\"host\":\"192.168.1.10\",\"port\":1883,\"topic\":\"jdrk/data\"}")
    @NotBlank(message = "连接参数不能为空")
    @Length(max = 2000, message = "连接参数长度不能超过 {max} 个字符")
    private String configJson;

    /**
     * 报文格式
     */
    @Schema(description = "报文格式（JSON / KV / CUSTOM）", example = "JSON")
    @Pattern(regexp = "JSON|KV|CUSTOM", message = "报文格式只能为 JSON、KV 或 CUSTOM")
    private String formatType;

    /**
     * MQTT QoS
     */
    @Schema(description = "MQTT QoS（0/1/2，仅 MQTT 目标使用）", example = "1")
    @Min(value = 0, message = "QoS 只能为 0、1 或 2")
    @Max(value = 2, message = "QoS 只能为 0、1 或 2")
    private Integer mqttQos;

    /**
     * MQTT 保留标志
     */
    @Schema(description = "MQTT 保留标志（仅 MQTT 目标使用）", example = "false")
    private Boolean mqttRetain;

    /**
     * 失败重试次数
     */
    @Schema(description = "失败重试次数", example = "3")
    @Min(value = 0, message = "重试次数不能小于 {value}")
    @Max(value = 10, message = "重试次数不能大于 {value}")
    private Integer retryCount;

    /**
     * 重试间隔
     */
    @Schema(description = "重试间隔（毫秒）", example = "5000")
    @Min(value = 100, message = "重试间隔不能小于 {value} 毫秒")
    @Max(value = 600000, message = "重试间隔不能大于 {value} 毫秒")
    private Integer retryIntervalMs;

    /**
     * 超时
     */
    @Schema(description = "超时（毫秒）", example = "10000")
    @Min(value = 100, message = "超时不能小于 {value} 毫秒")
    @Max(value = 600000, message = "超时不能大于 {value} 毫秒")
    private Integer timeoutMs;

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

    /**
     * 排序
     */
    @Schema(description = "排序", example = "1")
    @Min(value = 0, message = "排序不能小于 {value}")
    @Max(value = 999, message = "排序不能大于 {value}")
    private Integer sort;
}
