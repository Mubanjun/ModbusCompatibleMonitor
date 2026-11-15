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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.base.controller.BaseController;
import top.continew.admin.monitor.model.query.BizDataQuery;
import top.continew.admin.monitor.model.req.BizDataReq;
import top.continew.admin.monitor.model.req.DataCorrectReq;
import top.continew.admin.monitor.model.req.DataRecalculateReq;
import top.continew.admin.monitor.model.resp.BizDataResp;
import top.continew.admin.monitor.model.resp.DataAggregateResp;
import top.continew.admin.monitor.runtime.data.DataCorrectionService;
import top.continew.admin.monitor.runtime.model.DataCorrectResp;
import top.continew.admin.monitor.runtime.model.DataRecalculateResp;
import top.continew.admin.monitor.service.BizDataService;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.log.annotation.Log;

import java.util.List;

/**
 * 采集历史数据 API（FR-WEB-05/06/08）
 *
 * <p>数据由采集内核写入，只开放分页查询、详情与导出；
 * 查询时可通过 {@code recalculate=true} 按当前通道系数重算工程值用于展示。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Tag(name = "采集历史数据 API")
@RequiredArgsConstructor
@RestController
@CrudRequestMapping(value = "/monitor/data",
    api = {Api.PAGE, Api.LIST, Api.GET, Api.EXPORT})
public class BizDataController
    extends BaseController<BizDataService, BizDataResp, BizDataResp, BizDataQuery, BizDataReq> {

    private final BizDataService bizDataService;
    private final DataCorrectionService dataCorrectionService;

    /**
     * 按时间桶聚合历史数据（FR-WEB-05 聚合、FR-WEB-02 曲线取数）
     *
     * @param query 查询条件（须含时间范围）
     * @return 聚合结果
     */
    @Operation(summary = "按时间桶聚合历史数据", description = "按 MINUTE/HOUR/DAY 粒度聚合平均值、最小值、最大值与样本数")
    @SaCheckPermission("monitor:data:list")
    @GetMapping("/aggregate")
    public List<DataAggregateResp> aggregate(BizDataQuery query) {
        return bizDataService.aggregate(query);
    }

    /**
     * 按时间升序读取曲线点（FR-WEB-02 实时曲线取数）
     *
     * @param query 查询条件（须含时间范围）
     * @param limit 最大返回点数，默认 2000、上限 20000
     * @return 曲线点（时间升序）
     */
    @Operation(summary = "读取实时曲线点", description = "按时间升序返回指定窗口内的曲线点（单指标），用于前端实时曲线绘制")
    @SaCheckPermission("monitor:data:list")
    @GetMapping("/curve")
    public List<BizDataResp> curve(BizDataQuery query,
        @RequestParam(defaultValue = "2000") Integer limit) {
        return bizDataService.listCurve(query, limit);
    }

    /**
     * 单点修正历史数据（FR-WEB-08）
     *
     * @param req 请求
     * @return 修正结果
     */
    @Operation(summary = "单点修正历史数据", description = "按 ID 或设备/节点/模拟量/时间修正工程值或原始值并落库（FR-WEB-08）")
    @SaCheckPermission("monitor:data:correct")
    @Log
    @PostMapping("/correct")
    public DataCorrectResp correct(@RequestBody @Valid DataCorrectReq req) {
        return dataCorrectionService.correct(req);
    }

    /**
     * 批量补算历史数据（FR-WEB-08）
     *
     * @param req 请求
     * @return 补算结果
     */
    @Operation(summary = "批量补算历史数据", description = "按当前通道系数对已有原始值重算工程值并落库，支持预演（FR-WEB-08）")
    @SaCheckPermission("monitor:data:correct")
    @Log
    @PostMapping("/recalculate")
    public DataRecalculateResp recalculate(@RequestBody @Valid DataRecalculateReq req) {
        return dataCorrectionService.recalculate(req);
    }
}
