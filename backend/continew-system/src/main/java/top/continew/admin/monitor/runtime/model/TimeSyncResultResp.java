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
import java.time.LocalDateTime;

/**
 * 设备校时结果（NFR-REL-06）
 *
 * @author jdrk
 * @since 2026/10/1
 */
@Data
@Schema(description = "设备校时结果")
public class TimeSyncResultResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID")
    private Long deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "设备地址（从站地址）")
    private Integer deviceAddr;

    @Schema(description = "端口名称")
    private String portName;

    @Schema(description = "协议")
    private String protocol;

    @Schema(description = "触发方式（MANUAL：手动一键校时；AUTO：自动定时校时）")
    private String action;

    @Schema(description = "下发的平台本机时间")
    private LocalDateTime serverTime;

    @Schema(description = "校时前设备时间")
    private LocalDateTime deviceTimeBefore;

    @Schema(description = "校时前偏差（秒，设备时间减平台时间）")
    private Long offsetSecondsBefore;

    @Schema(description = "是否因偏差在容差内而跳过写入（仅自动校时会发生）")
    private Boolean writeSkipped;

    @Schema(description = "回读的设备时间")
    private LocalDateTime readbackTime;

    @Schema(description = "回读偏差（秒，回读时间减下发时间）")
    private Long readbackOffsetSeconds;

    @Schema(description = "回读是否与下发一致")
    private Boolean readbackOk;

    @Schema(description = "结果（SUCCESS：成功；SKIPPED：容差内跳过；FAILED：失败；REJECTED：被拒绝）")
    private String resultStatus;

    @Schema(description = "结果说明")
    private String message;

    @Schema(description = "耗时（毫秒）")
    private Long durationMs;
}
