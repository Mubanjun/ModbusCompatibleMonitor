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
import java.util.ArrayList;
import java.util.List;

/**
 * 设备校时状态与偏差检测（NFR-REL-06）
 *
 * <p>逐台读取设备时间寄存器（684~689）并与平台本机时间比对，得到偏差；
 * 偏差是否在容差内决定自动校时是否写入。</p>
 *
 * @author jdrk
 * @since 2026/10/1
 */
@Data
@Schema(description = "设备校时状态与偏差检测")
public class TimeSyncStatusResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "平台本机时间")
    private LocalDateTime serverTime;

    @Schema(description = "是否允许向设备下发时间（TIME_SYNC_WRITE_ENABLED）")
    private Boolean writeEnabled;

    @Schema(description = "是否启用自动定时校时（TIME_SYNC_AUTO_ENABLED）")
    private Boolean autoEnabled;

    @Schema(description = "自动校时计划（Spring cron 表达式）")
    private String autoCron;

    @Schema(description = "偏差容差（秒），偏差不超过该值视为准确")
    private Integer toleranceSeconds;

    @Schema(description = "时间寄存器起始地址")
    private Integer registerBase;

    @Schema(description = "最近一次校时（含自动）的执行时间")
    private LocalDateTime lastRunTime;

    @Schema(description = "最近一次校时的结果摘要")
    private String lastRunMessage;

    @Schema(description = "各设备时间偏差")
    private List<DeviceItem> devices = new ArrayList<>();

    /**
     * 单设备时间偏差
     */
    @Data
    @Schema(description = "单设备时间偏差")
    public static class DeviceItem implements Serializable {

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

        @Schema(description = "该协议是否支持校时（仁科 RS-Modbus 为只读规约，不支持）")
        private Boolean supported;

        @Schema(description = "是否在线（本次读取是否成功）")
        private Boolean online;

        @Schema(description = "设备时间")
        private LocalDateTime deviceTime;

        @Schema(description = "偏差（秒，设备时间减平台时间）")
        private Long offsetSeconds;

        @Schema(description = "偏差是否在容差内")
        private Boolean withinTolerance;

        @Schema(description = "说明（不支持校时或读取失败时的原因）")
        private String message;
    }
}
