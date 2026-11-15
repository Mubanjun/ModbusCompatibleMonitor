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

import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizCollectOrderMapper;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.model.entity.BizCollectOrderDO;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.query.BizCollectOrderQuery;
import top.continew.admin.monitor.model.req.BizCollectOrderReq;
import top.continew.admin.monitor.model.resp.BizCollectOrderResp;
import top.continew.admin.monitor.service.BizCollectOrderService;
import top.continew.starter.core.util.validation.CheckUtils;

/**
 * 采集顺序业务实现
 *
 * <p>采集顺序按端口维护设备的轮询次序（FR-ACQ-04），因此要求设备确实属于该端口。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
// @formatter:off
// 泛型参数拼接后单行会超出 Checkstyle 的 150 字符上限，而 Spotless 不会拆分泛型，故此处手工换行并豁免格式化
public class BizCollectOrderServiceImpl
    extends BaseServiceImpl<BizCollectOrderMapper, BizCollectOrderDO, BizCollectOrderResp,
        BizCollectOrderResp, BizCollectOrderQuery, BizCollectOrderReq>
    implements BizCollectOrderService {
    // @formatter:on

    private static final int DEFAULT_SORT_NO = 999;

    private final BizPortMapper bizPortMapper;
    private final BizDeviceMapper bizDeviceMapper;

    @Override
    public void beforeCreate(BizCollectOrderReq req) {
        this.fillDefaults(req);
        this.checkDevice(req);
        this.checkRepeat(req.getPortId(), req.getDeviceId(), null);
    }

    @Override
    public void beforeUpdate(BizCollectOrderReq req, Long id) {
        this.fillDefaults(req);
        this.checkDevice(req);
        this.checkRepeat(req.getPortId(), req.getDeviceId(), id);
    }

    /**
     * 填充默认值
     *
     * @param req 请求参数
     */
    private void fillDefaults(BizCollectOrderReq req) {
        if (req.getSortNo() == null) {
            req.setSortNo(DEFAULT_SORT_NO);
        }
    }

    /**
     * 校验端口存在且设备属于该端口
     *
     * @param req 请求参数
     */
    private void checkDevice(BizCollectOrderReq req) {
        CheckUtils.throwIf(bizPortMapper.selectById(req.getPortId()) == null, "所选端口不存在");
        BizDeviceDO device = bizDeviceMapper.selectById(req.getDeviceId());
        CheckUtils.throwIf(device == null, "所选设备不存在");
        CheckUtils.throwIf(!ObjectUtil.equal(device.getPortId(), req.getPortId()), "所选设备不属于该端口");
    }

    /**
     * 校验同一端口下同一设备只维护一条顺序记录
     *
     * @param portId   端口 ID
     * @param deviceId 设备 ID
     * @param id       ID
     */
    private void checkRepeat(Long portId, Long deviceId, Long id) {
        CheckUtils.throwIf(baseMapper.lambdaQuery()
            .eq(BizCollectOrderDO::getPortId, portId)
            .eq(BizCollectOrderDO::getDeviceId, deviceId)
            .ne(id != null, BizCollectOrderDO::getId, id)
            .exists(), "该端口下此设备的采集顺序已配置");
    }
}
