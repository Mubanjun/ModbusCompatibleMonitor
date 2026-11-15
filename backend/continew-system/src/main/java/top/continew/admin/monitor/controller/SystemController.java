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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.monitor.runtime.model.VersionResp;
import top.continew.starter.log.annotation.Log;

import java.lang.management.ManagementFactory;

/**
 * 平台信息 API（FR-QT-10 版本与更新检查）
 *
 * <p>版本接口刻意免登录：桌面端外壳在用户登录前就要展示版本并做更新比对，
 * 返回内容只有应用名/版本/构建时间/运行环境，不含任何凭据与业务数据。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Tag(name = "平台信息 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/system")
public class SystemController {

    private final Environment environment;

    @Value("${spring.application.name:continew-admin}")
    private String applicationName;

    /**
     * 查询平台版本
     *
     * @return 版本信息
     */
    @Operation(summary = "查询平台版本", description = "返回应用名、版本、构建时间与运行环境（FR-QT-10）")
    @GetMapping("/version")
    public VersionResp version() {
        VersionResp resp = new VersionResp();
        resp.setApplication(applicationName);
        resp.setVersion(SystemController.class.getPackage().getImplementationVersion());
        resp.setBuildTime(environment.getProperty("jdrk.build.time"));
        String[] profiles = environment.getActiveProfiles();
        resp.setProfile(
            profiles.length == 0 ? environment.getDefaultProfiles().length == 0 ? "default"
                : String.join(",", environment.getDefaultProfiles()) : String.join(",", profiles));
        resp.setJavaVersion(System.getProperty("java.version"));
        resp.setOs(System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        resp.setUptimeMs(ManagementFactory.getRuntimeMXBean().getUptime());
        return resp;
    }

    /**
     * 优雅停机（FR-SYS-06）
     *
     * <p>供桌面壳与运维脚本「停止服务」使用：先返回响应，再在独立线程里触发 JVM 正常退出，
     * 与在控制台按 Ctrl+C 等价——会执行完整关闭流程（停止采集、冲刷本地暂存、关闭连接池、
     * 释放单实例锁）。直接杀进程会跳过这些钩子，可能留下未落盘的暂存与残留锁。</p>
     *
     * <p>权限：仅授予需要停机权限的角色（默认只有超管）；操作写入审计日志。</p>
     *
     * @return 受理结果
     */
    @Operation(summary = "优雅停机", description = "触发 Spring 正常关闭流程（等价 Ctrl+C），供桌面壳/运维脚本停止服务")
    @SaCheckPermission("monitor:system:shutdown")
    @Log
    @PostMapping("/shutdown")
    public String shutdown() {
        log.warn("收到优雅停机指令，将在 1 秒后执行正常关闭流程（如需立即停止请直接结束进程）");
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }, "jdrk-graceful-shutdown");
        worker.setDaemon(false);
        worker.start();
        return "已受理优雅停机，进程将在 1 秒后退出";
    }
}
