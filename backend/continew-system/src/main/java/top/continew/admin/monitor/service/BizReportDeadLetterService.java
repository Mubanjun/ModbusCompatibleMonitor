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
import top.continew.admin.monitor.model.entity.BizReportDeadLetterDO;
import top.continew.admin.monitor.model.query.BizReportDeadLetterQuery;
import top.continew.admin.monitor.model.req.BizReportDeadLetterReq;
import top.continew.admin.monitor.model.resp.BizReportDeadLetterResp;
import top.continew.starter.data.service.IService;

import java.util.List;

/**
 * 上报死信业务接口（FR-RPT-05）
 *
 * @author jdrk
 * @since 2026/9/23
 */
public interface BizReportDeadLetterService
    extends
    BaseService<BizReportDeadLetterResp, BizReportDeadLetterResp, BizReportDeadLetterQuery, BizReportDeadLetterReq>,
    IService<BizReportDeadLetterDO> {

    /**
     * 人工重放死信
     *
     * @param ids 死信 ID
     * @return 重放结果说明
     */
    String replay(List<Long> ids);

    /**
     * 清理超出容量的死信
     *
     * @param keep 保留条数
     * @return 清理条数
     */
    int purgeOverflow(long keep);
}
