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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizPortDO;
import top.continew.admin.monitor.model.query.BizPortQuery;
import top.continew.admin.monitor.model.req.BizPortReq;
import top.continew.admin.monitor.model.resp.BizPortResp;
import top.continew.admin.monitor.service.BizPortService;
import top.continew.starter.core.util.validation.CheckUtils;

import java.util.List;

/**
 * 采集端口业务实现
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
public class BizPortServiceImpl
    extends
    BaseServiceImpl<BizPortMapper, BizPortDO, BizPortResp, BizPortResp, BizPortQuery, BizPortReq>
    implements BizPortService {

    private final BizDeviceMapper bizDeviceMapper;

    @Override
    public void beforeCreate(BizPortReq req) {
        this.fillDefaults(req);
        this.checkRepeat(req, null);
    }

    @Override
    public void beforeUpdate(BizPortReq req, Long id) {
        this.checkRepeat(req, id);
    }

    @Override
    public void beforeDelete(List<Long> ids) {
        // 端口下仍有设备时禁止删除，避免设备成为孤立数据
        List<BizPortDO> ports = baseMapper.lambdaQuery().in(BizPortDO::getId, ids).list();
        for (BizPortDO port : ports) {
            long deviceCount = bizDeviceMapper.lambdaQuery()
                .eq(BizDeviceDO::getPortId, port.getId())
                .count();
            CheckUtils.throwIf(deviceCount > 0, "端口 [{}] 下仍有 {} 台设备，请先删除或迁移设备",
                port.getName(), deviceCount);
        }
    }

    /**
     * 为未填写的串口参数补默认值（8N1、4800，与仁科设备常用参数一致）
     *
     * @param req 请求参数
     */
    private void fillDefaults(BizPortReq req) {
        if (req.getBaudRate() == null && "SERIAL".equals(req.getPortType())) {
            req.setBaudRate(4800);
        }
        if (req.getDataBits() == null) {
            req.setDataBits(8);
        }
        if (req.getStopBits() == null) {
            req.setStopBits(1);
        }
        if (req.getParity() == null) {
            req.setParity(0);
        }
        if (req.getFlowControl() == null) {
            req.setFlowControl(0);
        }
        if (req.getSort() == null) {
            req.setSort(999);
        }
    }

    /**
     * 检查同一端口类型下端口地址是否重复
     *
     * @param req 请求参数
     * @param id  ID
     */
    private void checkRepeat(BizPortReq req, Long id) {
        CheckUtils.throwIf(baseMapper.lambdaQuery()
            .eq(BizPortDO::getPortType, req.getPortType())
            .eq(BizPortDO::getPortName, req.getPortName())
            .ne(id != null, BizPortDO::getId, id)
            .exists(), "端口 [{}] 已存在", req.getPortName());
    }
}
