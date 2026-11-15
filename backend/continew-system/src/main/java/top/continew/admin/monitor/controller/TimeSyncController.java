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

package top.continew.admin.monitor.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.monitor.model.req.TimeSyncBatchReq;
import top.continew.admin.monitor.runtime.model.TimeSyncResultResp;
import top.continew.admin.monitor.runtime.model.TimeSyncStatusResp;
import top.continew.admin.monitor.runtime.timesync.DeviceTimeSyncService;
import top.continew.admin.monitor.runtime.timesync.TimeSyncMode;
import top.continew.starter.log.annotation.Log;

import java.util.List;

/**
 * 设备校时 API（NFR-REL-06）
 *
 * <p>标准 ModBus 规约下时间寄存器为 684~689（年、月、日、时、分、秒）；
 * 校时写入受系统配置 {@code TIME_SYNC_WRITE_ENABLED} 约束（FR-PROTO-09），
 * 自动定时校时由 {@code TIME_SYNC_AUTO_ENABLED} 与计划表达式控制。</p>
 *
 * @author jdrk
 * @since 2026/10/1
 */
@Tag(name = "设备校时 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/time-sync")
public class TimeSyncController {

    private final DeviceTimeSyncService deviceTimeSyncService;

    /**
     * 查询校时状态与设备时间偏差
     *
     * @return 校时状态
     */
    @Operation(summary = "查询校时状态",
        description = "返回平台时间、写保护与自动校时配置，并逐台读取设备时间给出偏差（NFR-REL-06 偏差可检测）")
    @SaCheckPermission("monitor:timeSync:status")
    @GetMapping("/status")
    public TimeSyncStatusResp status() {
        return deviceTimeSyncService.status();
    }

    /**
     * 单设备一键校时
     *
     * @param deviceId 设备 ID
     * @return 校时结果
     */
    @Operation(summary = "单设备一键校时", description = "把平台本机时间写入指定设备的时间寄存器并回读校验（NFR-REL-06）")
    @Parameter(name = "deviceId", description = "设备 ID", in = ParameterIn.PATH)
    @SaCheckPermission("monitor:timeSync:sync")
    @Log
    @PostMapping("/device/{deviceId}")
    public TimeSyncResultResp syncDevice(@PathVariable Long deviceId) {
        return deviceTimeSyncService.syncDevice(deviceId);
    }

    /**
     * 批量一键校时
     *
     * @param req 批量校时请求，设备列表为空表示全部启用设备
     * @return 各设备校时结果
     */
    @Operation(summary = "批量一键校时",
        description = "把平台本机时间写入全部（或指定）设备的时间寄存器并逐台回读校验；校时方式可选 ALL（全部下发）或 TOLERANT（仅偏差超出容差的设备下发，NFR-REL-06 可选择性校时）")
    @SaCheckPermission("monitor:timeSync:sync")
    @Log
    @PostMapping("/batch")
    public List<TimeSyncResultResp> syncAll(
        @RequestBody(required = false) @Valid TimeSyncBatchReq req) {
        if (req == null) {
            return deviceTimeSyncService.syncAll(null, TimeSyncMode.ALL);
        }
        return deviceTimeSyncService.syncAll(req.getDeviceIds(), TimeSyncMode.of(req.getMode()));
    }
}
