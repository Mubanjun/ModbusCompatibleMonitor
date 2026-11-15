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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.monitor.runtime.model.HeartbeatResp;
import top.continew.admin.monitor.runtime.model.SelfCheckItemResp;
import top.continew.admin.monitor.runtime.model.WatchdogStatusResp;
import top.continew.admin.monitor.runtime.watchdog.WatchdogProperties;
import top.continew.admin.monitor.runtime.watchdog.WatchdogService;
import top.continew.starter.core.util.validation.CheckUtils;
import top.continew.starter.log.annotation.Log;

import java.util.List;

/**
 * 看门狗 API（FR-WD-02/03/05/06）
 *
 * <p>心跳接口是唯一不要求登录的接口（看门狗进程独立于后端，无法拿到登录令牌），
 * 可通过 `jdrk.watchdog.heartbeat-token` 配置共享令牌加固。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Tag(name = "看门狗 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/watchdog")
public class WatchdogController {

    private final WatchdogService watchdogService;
    private final WatchdogProperties watchdogProperties;

    /**
     * 心跳（供看门狗轮询）
     *
     * @param request 请求
     * @return 心跳内容
     */
    @Operation(summary = "心跳", description = "返回进程与轻量检查结果，并刷新心跳文件（FR-WD-02）")
    @GetMapping("/heartbeat")
    public HeartbeatResp heartbeat(HttpServletRequest request) {
        String token = watchdogProperties.getHeartbeatToken();
        if (token != null && !token.isBlank()) {
            CheckUtils.throwIf(!token.equals(request.getHeader("X-Watchdog-Token")), "看门狗令牌不正确");
        }
        return watchdogService.heartbeat();
    }

    /**
     * 看门狗状态
     *
     * @return 状态
     */
    @Operation(summary = "看门狗状态", description = "心跳新鲜度、重启记录与最近自检结论（FR-WD-02/05/06）")
    @SaCheckPermission("monitor:watchdog:status")
    @GetMapping("/status")
    public WatchdogStatusResp status() {
        return watchdogService.status();
    }

    /**
     * 执行自检
     *
     * @return 检查项
     */
    @Operation(summary = "执行自检", description = "检查数据源、采集、端口、暂存、上报、同步并落库（FR-WD-06）")
    @SaCheckPermission("monitor:watchdog:selfCheck")
    @Log
    @PostMapping("/self-check")
    public List<SelfCheckItemResp> selfCheck() {
        return watchdogService.selfCheck();
    }

    /**
     * 落库看门狗事件
     *
     * @return 落库条数
     */
    @Operation(summary = "落库看门狗事件", description = "把看门狗进程写入的重启事件导入 biz_watchdog_log（FR-WD-03）")
    @SaCheckPermission("monitor:watchdog:selfCheck")
    @Log
    @PostMapping("/ingest")
    public Integer ingest() {
        return watchdogService.ingest();
    }
}
