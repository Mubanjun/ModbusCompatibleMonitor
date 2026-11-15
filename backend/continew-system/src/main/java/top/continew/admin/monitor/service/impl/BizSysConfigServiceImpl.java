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
import top.continew.admin.monitor.mapper.BizSysConfigMapper;
import top.continew.admin.monitor.model.entity.BizSysConfigDO;
import top.continew.admin.monitor.model.query.BizSysConfigQuery;
import top.continew.admin.monitor.model.req.BizSysConfigReq;
import top.continew.admin.monitor.model.resp.BizSysConfigResp;
import top.continew.admin.monitor.service.BizSysConfigService;
import top.continew.starter.core.util.validation.CheckUtils;

/**
 * 业务系统配置业务实现
 *
 * <p>对应 FR-CFG-01~04 的四类参数。参数编码是运行时按码取值的键，
 * 因此与基座字典一致：**创建后不允许修改编码**。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
public class BizSysConfigServiceImpl
    extends
    BaseServiceImpl<BizSysConfigMapper, BizSysConfigDO, BizSysConfigResp, BizSysConfigResp, BizSysConfigQuery, BizSysConfigReq>
    implements BizSysConfigService {

    @Override
    public void beforeCreate(BizSysConfigReq req) {
        this.checkCodeRepeat(req.getCode(), null);
    }

    @Override
    public void beforeUpdate(BizSysConfigReq req, Long id) {
        this.checkCodeRepeat(req.getCode(), id);
        BizSysConfigDO oldConfig = super.getById(id);
        CheckUtils.throwIf(oldConfig != null && !oldConfig.getCode().equals(req.getCode()),
            "不允许修改参数编码");
    }

    /**
     * 检查参数编码是否重复
     *
     * @param code 参数编码
     * @param id   ID
     */
    private void checkCodeRepeat(String code, Long id) {
        CheckUtils.throwIf(baseMapper.lambdaQuery()
            .eq(BizSysConfigDO::getCode, code)
            .ne(id != null, BizSysConfigDO::getId, id)
            .exists(), "参数编码 [{}] 已存在", code);
    }
}
