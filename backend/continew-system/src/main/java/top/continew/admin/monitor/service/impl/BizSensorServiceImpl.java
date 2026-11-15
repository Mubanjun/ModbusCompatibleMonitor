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

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizDeviceGroupMapper;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.mapper.BizSensorMapper;
import top.continew.admin.monitor.model.entity.BizSensorDO;
import top.continew.admin.monitor.model.query.BizSensorQuery;
import top.continew.admin.monitor.model.req.BizSensorReq;
import top.continew.admin.monitor.model.resp.BizSensorResp;
import top.continew.admin.monitor.service.BizSensorService;
import top.continew.starter.core.util.validation.CheckUtils;

/** 传感器对象业务实现。 */
@Service
@RequiredArgsConstructor
public class BizSensorServiceImpl
    extends
    BaseServiceImpl<BizSensorMapper, BizSensorDO, BizSensorResp, BizSensorResp, BizSensorQuery, BizSensorReq>
    implements BizSensorService {

    private static final long DEFAULT_GROUP_ID = 1L;
    private final BizPortMapper portMapper;
    private final BizDeviceMapper deviceMapper;
    private final BizDeviceGroupMapper groupMapper;

    @Override
    public void beforeCreate(BizSensorReq req) {
        fillDefaults(req);
        validateRefs(req);
    }

    @Override
    public void beforeUpdate(BizSensorReq req, Long id) {
        fillDefaults(req);
        validateRefs(req);
    }

    private void fillDefaults(BizSensorReq req) {
        if (req.getGroupId() == null) {
            req.setGroupId(DEFAULT_GROUP_ID);
        }
        if (req.getCoefficient() == null) {
            req.setCoefficient(BigDecimal.ONE);
        }
        if (req.getOffsetValue() == null) {
            req.setOffsetValue(BigDecimal.ZERO);
        }
    }

    private void validateRefs(BizSensorReq req) {
        CheckUtils.throwIf(portMapper.selectById(req.getPortId()) == null, "所属端口不存在");
        CheckUtils.throwIf(groupMapper.selectById(req.getGroupId()) == null, "所属分组不存在");
        if (req.getDeviceId() != null) {
            CheckUtils.throwIf(deviceMapper.selectById(req.getDeviceId()) == null, "所属设备不存在");
        }
    }
}
