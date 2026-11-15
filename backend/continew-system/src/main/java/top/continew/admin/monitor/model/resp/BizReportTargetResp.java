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
 * 上报目标响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "上报目标响应参数")
public class BizReportTargetResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标名称
     */
    @Schema(description = "目标名称", example = "厂区 MQTT Broker")
    private String name;

    /**
     * 目标类型
     */
    @Schema(description = "目标类型（MQTT / DB / HTTP）", example = "MQTT")
    private String targetType;

    /**
     * 数据库方言
     */
    @Schema(description = "数据库方言", example = "POSTGRESQL")
    private String dbType;

    /**
     * 连接参数（JSON）
     */
    @Schema(description = "连接参数（JSON）")
    private String configJson;

    /**
     * 报文格式
     */
    @Schema(description = "报文格式（JSON / KV / CUSTOM）", example = "JSON")
    private String formatType;

    /**
     * MQTT QoS
     */
    @Schema(description = "MQTT QoS（0/1/2）", example = "1")
    private Integer mqttQos;

    /**
     * MQTT 保留标志
     */
    @Schema(description = "MQTT 保留标志", example = "false")
    private Boolean mqttRetain;

    /**
     * 失败重试次数
     */
    @Schema(description = "失败重试次数", example = "3")
    private Integer retryCount;

    /**
     * 重试间隔（毫秒）
     */
    @Schema(description = "重试间隔（毫秒）", example = "5000")
    private Integer retryIntervalMs;

    /**
     * 超时（毫秒）
     */
    @Schema(description = "超时（毫秒）", example = "10000")
    private Integer timeoutMs;

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

    /**
     * 排序
     */
    @Schema(description = "排序", example = "1")
    private Integer sort;
}
