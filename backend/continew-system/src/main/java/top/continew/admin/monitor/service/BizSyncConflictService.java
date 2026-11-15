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
import top.continew.admin.monitor.model.entity.BizSyncConflictDO;
import top.continew.admin.monitor.model.query.BizSyncConflictQuery;
import top.continew.admin.monitor.model.resp.BizSyncConflictResp;
import top.continew.starter.data.service.IService;

/**
 * 同步冲突业务接口
 *
 * <p>运行时数据，控制器只开放列表与详情，不提供增删改。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
public interface BizSyncConflictService extends
    BaseService<BizSyncConflictResp, BizSyncConflictResp, BizSyncConflictQuery, BizSyncConflictResp>,
    IService<BizSyncConflictDO> {
}
