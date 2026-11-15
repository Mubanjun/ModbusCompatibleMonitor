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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.base.controller.BaseController;
import top.continew.admin.monitor.model.query.BizReportDeadLetterQuery;
import top.continew.admin.monitor.model.req.BizReportDeadLetterReq;
import top.continew.admin.monitor.model.resp.BizReportDeadLetterResp;
import top.continew.admin.monitor.service.BizReportDeadLetterService;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.req.IdsReq;
import top.continew.starter.log.annotation.Log;

/**
 * 上报死信 API（FR-RPT-05）
 *
 * <p>死信由上报引擎写入，开放分页查询、详情与人工重放。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Tag(name = "上报死信 API")
@RequiredArgsConstructor
@RestController
@CrudRequestMapping(value = "/monitor/report/dead-letter",
    api = {Api.PAGE, Api.LIST, Api.GET})
public class BizReportDeadLetterController
    extends
    BaseController<BizReportDeadLetterService, BizReportDeadLetterResp, BizReportDeadLetterResp, BizReportDeadLetterQuery, BizReportDeadLetterReq> {

    private final BizReportDeadLetterService bizReportDeadLetterService;

    /**
     * 人工重放死信
     *
     * @param req 死信 ID 列表
     * @return 重放结果说明
     */
    @Operation(summary = "人工重放死信", description = "按原目标与原报文重新上报，并记录重放结果（FR-RPT-05）")
    @SaCheckPermission("monitor:deadLetter:replay")
    @Log
    @PostMapping("/replay")
    public String replay(@RequestBody @Valid IdsReq req) {
        return bizReportDeadLetterService.replay(req.getIds());
    }
}
