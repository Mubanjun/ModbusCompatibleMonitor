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
import top.continew.admin.monitor.model.req.RelayBatchReq;
import top.continew.admin.monitor.model.req.RelayControlReq;
import top.continew.admin.monitor.runtime.model.RelayControlResp;
import top.continew.admin.monitor.runtime.model.RelayStatusResp;
import top.continew.admin.monitor.runtime.relay.RelayService;
import top.continew.starter.log.annotation.Log;

/**
 * 继电器控制 API（FR-RLY-01~04）
 *
 * <p>标准 Modbus 规约下寄存器 696~703 对应 1~8 号继电器；控制操作受系统配置
 * {@code RELAY_WRITE_ENABLED} 约束，且每次操作都会写审计记录。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Tag(name = "继电器控制 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/relay")
public class RelayController {

    private final RelayService relayService;

    /**
     * 查询继电器状态
     *
     * @param deviceId 设备 ID
     * @return 状态
     */
    @Operation(summary = "查询继电器状态", description = "读取设备各继电器当前状态与在线情况（FR-RLY-01）")
    @Parameter(name = "deviceId", description = "设备 ID", in = ParameterIn.PATH)
    @SaCheckPermission("monitor:relay:status")
    @GetMapping("/{deviceId}/status")
    public RelayStatusResp status(@PathVariable Long deviceId) {
        return relayService.status(deviceId);
    }

    /**
     * 单路继电器控制
     *
     * @param deviceId 设备 ID
     * @param req      控制参数
     * @return 操作结果
     */
    @Operation(summary = "单路继电器控制", description = "对指定继电器执行闭合/断开并回读校验；支持执行时长（0 表示保持）")
    @Parameter(name = "deviceId", description = "设备 ID", in = ParameterIn.PATH)
    @SaCheckPermission("monitor:relay:control")
    @Log
    @PostMapping("/{deviceId}/control")
    public RelayControlResp control(@PathVariable Long deviceId,
        @RequestBody @Valid RelayControlReq req) {
        return relayService.control(deviceId, req.getRelayNo(), req.getClosed(), req
            .getDurationMs());
    }

    /**
     * 批量继电器控制
     *
     * @param deviceId 设备 ID
     * @param req      批量控制参数
     * @return 操作结果
     */
    @Operation(summary = "批量继电器控制", description = "按序号顺序批量写入多路继电器状态并回读校验（FR-RLY-03）")
    @Parameter(name = "deviceId", description = "设备 ID", in = ParameterIn.PATH)
    @SaCheckPermission("monitor:relay:control")
    @Log
    @PostMapping("/{deviceId}/batch")
    public RelayControlResp batch(@PathVariable Long deviceId,
        @RequestBody @Valid RelayBatchReq req) {
        return relayService.batch(deviceId, req.getValues());
    }

    /**
     * 查询继电器写入开关
     *
     * @return 是否允许写入
     */
    @Operation(summary = "查询继电器写入开关", description = "返回系统配置 RELAY_WRITE_ENABLED 的当前取值（FR-PROTO-09）")
    @SaCheckPermission("monitor:relay:status")
    @GetMapping("/write-enabled")
    public Boolean writeEnabled() {
        return relayService.writeEnabled();
    }
}
