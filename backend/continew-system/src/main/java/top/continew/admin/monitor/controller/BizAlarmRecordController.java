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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.base.controller.BaseController;
import top.continew.admin.monitor.model.query.BizAlarmRecordQuery;
import top.continew.admin.monitor.model.req.AlarmHandleReq;
import top.continew.admin.monitor.model.resp.BizAlarmRecordResp;
import top.continew.admin.monitor.service.BizAlarmRecordService;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.log.annotation.Log;

/**
 * 报警记录 API
 *
 * <p>运行时生成数据，只开放列表与详情。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Tag(name = "报警记录 API")
@RequiredArgsConstructor
@RestController
@CrudRequestMapping(value = "/monitor/alarm/record", api = {Api.PAGE, Api.LIST, Api.GET})
public class BizAlarmRecordController
    extends
    BaseController<BizAlarmRecordService, BizAlarmRecordResp, BizAlarmRecordResp, BizAlarmRecordQuery, BizAlarmRecordResp> {

    private final BizAlarmRecordService bizAlarmRecordService;

    /**
     * 登记报警处理结果
     *
     * @param id  记录 ID
     * @param req 处理参数
     */
    @Operation(summary = "登记报警处理结果", description = "登记处理方式、处理人与处理时间（FR-ALM-03）")
    @Parameter(name = "id", description = "记录 ID", in = ParameterIn.PATH)
    @SaCheckPermission("monitor:alarmRecord:update")
    @Log
    @PatchMapping("/{id}/handle")
    public void handle(@PathVariable Long id, @RequestBody @Valid AlarmHandleReq req) {
        bizAlarmRecordService.handle(id, req.getHandlingFlag(), req.getHandlingMethod());
    }
}
