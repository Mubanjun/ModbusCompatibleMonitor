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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.monitor.model.req.MigrationReq;
import top.continew.admin.monitor.runtime.migration.MigrationProbeService;
import top.continew.admin.monitor.runtime.migration.MigrationService;
import top.continew.admin.monitor.runtime.migration.MigrationSpec;
import top.continew.admin.monitor.runtime.model.MigrationDetectResp;
import top.continew.admin.monitor.runtime.model.MigrationImportResp;
import top.continew.admin.monitor.runtime.model.MigrationVerifyResp;
import top.continew.starter.log.annotation.Log;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 迁移向导 API（FR-IO-06、FR-MIG-01~06）
 *
 * <p>四步向导：检测原库 → 映射设备 → 导入历史 → 一致性校验；并提供运行模式（并存/切换）、
 * 回退指引与一致性报告查询。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Tag(name = "迁移向导 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/migration")
public class MigrationController {

    private final MigrationService migrationService;
    private final MigrationProbeService migrationProbeService;

    /**
     * 检测原库
     *
     * @param req 请求
     * @return 探测结果
     */
    @Operation(summary = "检测原库", description = "连接仁科原库并识别 DR-03 标准表与疑似设备表（FR-IO-06 第 1 步）")
    @SaCheckPermission("monitor:migration:detect")
    @PostMapping("/detect")
    public MigrationDetectResp detect(@RequestBody @Valid MigrationReq req) {
        return migrationProbeService.detect(spec(req));
    }

    /**
     * 映射设备
     *
     * @param req 请求
     * @return 导入结果
     */
    @Operation(summary = "映射设备", description = "按列映射把原库设备清单导入本系统（FR-IO-06 第 2 步、FR-MIG-03）")
    @SaCheckPermission("monitor:migration:import")
    @Log
    @PostMapping("/import-devices")
    public MigrationImportResp importDevices(@RequestBody @Valid MigrationReq req) {
        return migrationProbeService.importDevices(spec(req), req.getTable(), req.getPortId(), req
            .getProtocol(), req.getNameColumn(), req.getAddrColumn(),
            Boolean.TRUE.equals(req
                .getDryRun()));
    }

    /**
     * 导入历史数据
     *
     * @param req 请求
     * @return 导入结果
     */
    @Operation(summary = "导入历史数据", description = "按时间范围把原库历史迁移进本系统（FR-IO-06 第 3 步、FR-MIG-04）")
    @SaCheckPermission("monitor:migration:import")
    @Log
    @PostMapping("/import-history")
    public MigrationImportResp importHistory(@RequestBody @Valid MigrationReq req) {
        LocalDateTime[] range = range(req);
        return migrationService.importHistory(spec(req), range[0], range[1], Boolean.TRUE.equals(req
            .getDryRun()));
    }

    /**
     * 一致性校验
     *
     * @param req 请求
     * @return 校验报告
     */
    @Operation(summary = "一致性校验", description = "对比同一时间段的源库与本系统数据并产出报告（FR-MIG-06）")
    @SaCheckPermission("monitor:migration:verify")
    @Log
    @PostMapping("/verify")
    public MigrationVerifyResp verify(@RequestBody @Valid MigrationReq req) {
        LocalDateTime[] range = range(req);
        return migrationService.verify(spec(req), range[0], range[1]);
    }

    /**
     * 最近一次一致性校验报告
     *
     * @return 报告
     */
    @Operation(summary = "最近一致性报告", description = "返回进程内最近一次校验报告（FR-MIG-06）")
    @SaCheckPermission("monitor:migration:verify")
    @GetMapping("/verify/last")
    public MigrationVerifyResp lastVerify() {
        return migrationService.lastVerify();
    }

    /**
     * 迁移状态
     *
     * @return 状态
     */
    @Operation(summary = "迁移状态", description = "运行模式、错峰窗口、批量参数与最近校验（FR-MIG-01）")
    @SaCheckPermission("monitor:migration:detect")
    @GetMapping("/status")
    public Map<String, Object> status() {
        return migrationService.status();
    }

    /**
     * 切换运行模式
     *
     * @param req 请求
     * @return 生效模式
     */
    @Operation(summary = "切换运行模式", description = "OFF / COEXIST（并存错峰）/ SWITCHED（已切换），FR-MIG-01")
    @SaCheckPermission("monitor:migration:switch")
    @Log
    @PutMapping("/mode")
    public String setMode(@RequestBody @Valid MigrationReq req) {
        return migrationService.setMode(req.getMode());
    }

    /**
     * 回退指引
     *
     * @return 步骤
     */
    @Operation(summary = "回退指引",
        description = "自包含回退与设备交还的步骤（FR-MIG-05）：只回退本平台自身的运行模式与数据，并把设备交还现场，不启动、不调用、不依赖任何第三方原软件")
    @SaCheckPermission("monitor:migration:detect")
    @GetMapping("/rollback-guide")
    public List<String> rollbackGuide() {
        return List.of("1. 预演：pwsh tools/rollback-self-contained.ps1 -DryRun 只做就绪检查与计划打印，零副作用",
            "2. 在线阶段：把运行模式切回 OFF（PUT /monitor/migration/mode {\"mode\":\"OFF\"}），使错峰并存假设失效",
            "3. 留档：GET /monitor/io/export/data 导出本系统期间的数据；如需回到迁移前的库状态，用 backups/ 里的备份包执行恢复（POST /monitor/io/restore 或页面「导入导出与备份」）",
            "4. 停机：pwsh tools/rollback-self-contained.ps1 停止本平台后端与看门狗，释放串口/TCP 设备句柄",
            "5. 交还：由业主按其现场规程接管设备——本平台不启动、不调用、不依赖任何第三方原软件（防侵权口径）",
            "6. 归档：把本次回退记录写入 docs/迁移与无损替换验证记录.md");
    }

    private MigrationSpec spec(MigrationReq req) {
        return new MigrationSpec(req.getHost(), req.getPort(), req.getDatabase(), req.getUsername(),
            req
                .getPassword());
    }

    private LocalDateTime[] range(MigrationReq req) {
        LocalDateTime end = req.getEndTime() == null ? LocalDateTime.now() : req.getEndTime();
        LocalDateTime start = req.getStartTime() == null ? end.minusDays(30) : req.getStartTime();
        return new LocalDateTime[] {start, end};
    }
}
