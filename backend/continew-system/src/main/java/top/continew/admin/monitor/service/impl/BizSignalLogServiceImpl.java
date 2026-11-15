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

package top.continew.admin.monitor.service.impl;

import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizSignalLogMapper;
import top.continew.admin.monitor.model.entity.BizSignalLogDO;
import top.continew.admin.monitor.model.query.BizSignalLogQuery;
import top.continew.admin.monitor.model.req.BizSignalLogReq;
import top.continew.admin.monitor.model.resp.BizSignalLogResp;
import top.continew.admin.monitor.service.BizSignalLogService;

import java.time.LocalDateTime;

/**
 * 信令记录业务实现
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
// @formatter:off
// 泛型参数拼接后单行会超出 Checkstyle 的 150 字符上限，而 Spotless 不会拆分泛型，故此处手工换行并豁免格式化
public class BizSignalLogServiceImpl
    extends BaseServiceImpl<BizSignalLogMapper, BizSignalLogDO, BizSignalLogResp, BizSignalLogResp, BizSignalLogQuery,
        BizSignalLogReq>
    implements BizSignalLogService {
    // @formatter:on

    @Override
    public int purgeBefore(LocalDateTime deadline) {
        return baseMapper.purgeBefore(deadline);
    }
}
