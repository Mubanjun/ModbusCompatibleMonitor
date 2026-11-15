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

package top.continew.admin.monitor.runtime.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设备运行状态（FR-ACQ-10：成功率、平均耗时、超时次数、最后通讯时间）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "设备运行状态")
public class DeviceStatusResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID")
    private Long id;

    @Schema(description = "设备名称")
    private String name;

    @Schema(description = "从站地址")
    private Integer slaveAddr;

    @Schema(description = "协议")
    private String protocol;

    @Schema(description = "设备状态（UNKNOWN / ONLINE / OFFLINE / DISABLED / ALARM / COMM_ERROR）")
    private String status;

    @Schema(description = "设备状态中文名")
    private String statusLabel;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否暂停")
    private Boolean paused;

    @Schema(description = "生效采集间隔（毫秒）")
    private Integer intervalMs;

    @Schema(description = "通道数量")
    private Integer channelCount;

    @Schema(description = "最后轮询时间（毫秒时间戳）")
    private Long lastPollTime;

    @Schema(description = "最后通讯成功时间（毫秒时间戳）")
    private Long lastSuccessTime;

    @Schema(description = "轮询次数")
    private Long pollCount;

    @Schema(description = "成功次数")
    private Long successCount;

    @Schema(description = "超时次数")
    private Long timeoutCount;

    @Schema(description = "失败次数")
    private Long errorCount;

    @Schema(description = "成功率")
    private Double successRate;

    @Schema(description = "平均耗时（毫秒）")
    private Long averageDurationMs;

    @Schema(description = "熔断状态（CLOSED / OPEN / HALF_OPEN）")
    private String circuitState;

    @Schema(description = "连续失败次数")
    private Integer consecutiveFailures;

    @Schema(description = "熔断累计次数")
    private Integer tripCount;

    @Schema(description = "本轮是否超出轮询预算")
    private Boolean budgetExceeded;

    @Schema(description = "最近错误")
    private String lastError;
}
