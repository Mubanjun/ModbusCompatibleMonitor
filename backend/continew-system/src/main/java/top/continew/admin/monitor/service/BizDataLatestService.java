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
import top.continew.admin.monitor.model.entity.BizDataLatestDO;
import top.continew.admin.monitor.model.query.BizDataLatestQuery;
import top.continew.admin.monitor.model.req.BizDataLatestReq;
import top.continew.admin.monitor.model.resp.BizDataLatestResp;
import top.continew.starter.data.service.IService;

import java.util.List;

/**
 * 通道实时值业务接口
 *
 * @author jdrk
 * @since 2026/9/23
 */
public interface BizDataLatestService
    extends BaseService<BizDataLatestResp, BizDataLatestResp, BizDataLatestQuery, BizDataLatestReq>,
    IService<BizDataLatestDO> {

    /**
     * 按「设备 + 通道 + 模拟量」upsert 一批实时值
     *
     * @param rows 实时值
     * @return 处理条数
     */
    int upsertAll(List<BizDataLatestDO> rows);

    /**
     * 更新指定设备的实时值状态位（FR-WEB-03 状态标识）
     *
     * @param deviceId 设备 ID
     * @param status   设备状态
     * @return 更新条数
     */
    int updateDeviceStatus(Long deviceId, String status);
}
