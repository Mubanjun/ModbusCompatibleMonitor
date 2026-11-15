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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.base.controller.BaseController;
import top.continew.admin.monitor.model.query.BizSyncTaskQuery;
import top.continew.admin.monitor.model.req.BizSyncTaskReq;
import top.continew.admin.monitor.model.resp.BizSyncTaskResp;
import top.continew.admin.monitor.runtime.model.SyncProgressResp;
import top.continew.admin.monitor.runtime.sync.SyncEngine;
import top.continew.admin.monitor.service.BizSyncTaskService;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.log.annotation.Log;

/**
 * 同步任务 API（FR-SYNC-01/04/06）
 *
 * <p>除基础 CRUD 外，提供一键同步、暂停、续传与进度查询。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Tag(name = "同步任务 API")
@RequiredArgsConstructor
@RestController
@CrudRequestMapping(value = "/monitor/sync/task",
    api = {Api.PAGE, Api.LIST, Api.GET, Api.CREATE, Api.UPDATE, Api.BATCH_DELETE})
public class BizSyncTaskController
    extends
    BaseController<BizSyncTaskService, BizSyncTaskResp, BizSyncTaskResp, BizSyncTaskQuery, BizSyncTaskReq> {

    private final SyncEngine syncEngine;

    /**
     * 一键同步（全量从头、增量从水位）
     *
     * @param id 任务 ID
     * @return 提示信息
     */
    @Operation(summary = "一键同步", description = "全量模式从头同步，增量模式从水位线开始（FR-SYNC-01/04）")
    @Parameter(name = "id", description = "任务 ID", in = ParameterIn.PATH)
    @SaCheckPermission("monitor:syncTask:start")
    @Log
    @PostMapping("/{id}/start")
    public String start(@PathVariable Long id) {
        return syncEngine.start(id);
    }

    /**
     * 暂停同步
     *
     * @param id 任务 ID
     * @return 是否已请求暂停
     */
    @Operation(summary = "暂停同步", description = "当前批次结束后暂停，水位线保留以便续传（FR-SYNC-06）")
    @Parameter(name = "id", description = "任务 ID", in = ParameterIn.PATH)
    @SaCheckPermission("monitor:syncTask:pause")
    @Log
    @PostMapping("/{id}/pause")
    public boolean pause(@PathVariable Long id) {
        return syncEngine.pause(id);
    }

    /**
     * 续传同步
     *
     * @param id 任务 ID
     * @return 提示信息
     */
    @Operation(summary = "续传同步", description = "从水位线继续同步（FR-SYNC-06）")
    @Parameter(name = "id", description = "任务 ID", in = ParameterIn.PATH)
    @SaCheckPermission("monitor:syncTask:resume")
    @Log
    @PostMapping("/{id}/resume")
    public String resume(@PathVariable Long id) {
        return syncEngine.resume(id);
    }

    /**
     * 查询同步进度
     *
     * @param id 任务 ID
     * @return 进度
     */
    @Operation(summary = "查询同步进度", description = "返回状态、总数、成功数、冲突数与运行时长（FR-SYNC-06）")
    @Parameter(name = "id", description = "任务 ID", in = ParameterIn.PATH)
    @SaCheckPermission("monitor:syncTask:get")
    @GetMapping("/{id}/progress")
    public SyncProgressResp progress(@PathVariable Long id) {
        return syncEngine.progress(id);
    }
}
