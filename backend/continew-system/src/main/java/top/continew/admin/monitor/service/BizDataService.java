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

package top.continew.admin.monitor.service;

import top.continew.admin.common.base.service.BaseService;
import top.continew.admin.monitor.model.entity.BizDataDO;
import top.continew.admin.monitor.model.query.BizDataQuery;
import top.continew.admin.monitor.model.req.BizDataReq;
import top.continew.admin.monitor.model.resp.BizDataResp;
import top.continew.admin.monitor.model.resp.DataAggregateResp;
import top.continew.starter.data.service.IService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集历史数据业务接口
 *
 * @author jdrk
 * @since 2026/9/23
 */
public interface BizDataService
    extends BaseService<BizDataResp, BizDataResp, BizDataQuery, BizDataReq>, IService<BizDataDO> {

    /**
     * 幂等批量写入历史数据（按来源暂存主键去重，NFR-REL-05）
     *
     * @param rows 待写入记录
     * @return 实际新增条数
     */
    int saveIdempotent(List<BizDataDO> rows);

    /**
     * 按时间桶聚合历史数据
     *
     * @param query 查询条件（须含时间范围与粒度）
     * @return 聚合结果
     */
    List<DataAggregateResp> aggregate(BizDataQuery query);

    /**
     * 按时间升序读取曲线点（FR-WEB-02 实时曲线取数）
     *
     * @param query 查询条件（须含时间范围）
     * @param limit 期望的最大点数（超过上限会被收敛）
     * @return 曲线点（时间升序）
     */
    List<BizDataResp> listCurve(BizDataQuery query, Integer limit);

    /**
     * 物理清理超过保留期的历史数据（DR-05）
     *
     * @param deadline 早于该时间的记录将被删除
     * @return 删除行数
     */
    int purgeBefore(LocalDateTime deadline);
}
