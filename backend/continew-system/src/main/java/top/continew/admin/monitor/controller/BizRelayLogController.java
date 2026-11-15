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

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.base.controller.BaseController;
import top.continew.admin.monitor.model.query.BizRelayLogQuery;
import top.continew.admin.monitor.model.req.BizRelayLogReq;
import top.continew.admin.monitor.model.resp.BizRelayLogResp;
import top.continew.admin.monitor.service.BizRelayLogService;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;

/**
 * 继电器操作日志 API（FR-RLY-04）
 *
 * <p>审计记录由继电器控制接口写入，开放分页查询、详情与导出。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Tag(name = "继电器操作日志 API")
@RestController
@CrudRequestMapping(value = "/monitor/relay/log",
    api = {Api.PAGE, Api.LIST, Api.GET, Api.EXPORT})
public class BizRelayLogController
    extends
    BaseController<BizRelayLogService, BizRelayLogResp, BizRelayLogResp, BizRelayLogQuery, BizRelayLogReq> {
}
