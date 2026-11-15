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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.base.controller.BaseController;
import top.continew.admin.monitor.model.query.BizSignalLogQuery;
import top.continew.admin.monitor.model.req.BizSignalLogReq;
import top.continew.admin.monitor.model.resp.BizSignalLogResp;
import top.continew.admin.monitor.service.BizSignalLogService;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.extension.crud.model.query.PageQuery;

import java.util.List;

/**
 * 信令记录 API（FR-SIG-01/02/03）
 *
 * <p>信令由采集内核写入，只开放列表与详情。未分页列表用于前端下拉选择，
 * 因此**有返回上限**：该表是高写入量诊断表，实测 5.2 万行时整表拉回会让请求超时。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Tag(name = "信令记录 API")
@RestController
@CrudRequestMapping(value = "/monitor/signal/log", api = {Api.PAGE, Api.GET})
public class BizSignalLogController
    extends
    BaseController<BizSignalLogService, BizSignalLogResp, BizSignalLogResp, BizSignalLogQuery, BizSignalLogReq> {

    /** 未分页列表的返回上限（供下拉使用，够用且不会把整表拉回） */
    private static final int MAX_LIST_ROWS = 1000;

    /**
     * 查询信令记录列表（最多 {@value #MAX_LIST_ROWS} 条）
     *
     * @param query 查询条件
     * @return 信令记录
     */
    @Operation(summary = "查询信令记录列表", description = "按条件查询，最多返回 1000 条（供下拉选择）")
    // 权限串必须与菜单表登记的按钮权限一致（monitor:signalLog:list），
    // 否则该接口只有超管能用、普通角色无法授权（审计缺陷 38）
    @SaCheckPermission("monitor:signalLog:list")
    @GetMapping("/list")
    public List<BizSignalLogResp> list(BizSignalLogQuery query) {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(1);
        pageQuery.setSize(MAX_LIST_ROWS);
        return this.page(query, pageQuery).getList();
    }
}
