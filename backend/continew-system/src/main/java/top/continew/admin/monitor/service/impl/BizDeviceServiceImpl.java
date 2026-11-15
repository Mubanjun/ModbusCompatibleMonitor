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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizChannelMapper;
import top.continew.admin.monitor.mapper.BizDeviceGroupMapper;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.model.entity.BizChannelDO;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizDeviceGroupDO;
import top.continew.admin.monitor.model.entity.BizPortDO;
import top.continew.admin.monitor.model.query.BizDeviceQuery;
import top.continew.admin.monitor.model.req.BizDeviceReq;
import top.continew.admin.monitor.model.resp.BizDeviceResp;
import top.continew.admin.monitor.service.BizDeviceService;
import top.continew.admin.monitor.support.DeviceGroupAccessService;
import top.continew.starter.core.util.validation.CheckUtils;

import java.util.List;

/**
 * 采集设备业务实现
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
public class BizDeviceServiceImpl
    extends
    BaseServiceImpl<BizDeviceMapper, BizDeviceDO, BizDeviceResp, BizDeviceResp, BizDeviceQuery, BizDeviceReq>
    implements BizDeviceService {

    private static final String DEFAULT_PROTOCOL = "RS_MODBUS";
    private static final int DEFAULT_TIMEOUT_MS = 1000;
    private static final int DEFAULT_SORT_NO = 999;
    private static final long DEFAULT_GROUP_ID = 1L;

    private final BizPortMapper bizPortMapper;
    private final BizChannelMapper bizChannelMapper;
    private final BizDeviceGroupMapper bizDeviceGroupMapper;
    private final DeviceGroupAccessService accessService;

    @Override
    public BizDeviceResp get(Long id) {
        BizDeviceDO device = baseMapper.selectRawById(id);
        CheckUtils.throwIf(device == null, "设备不存在");
        accessService.assertAllowed(device.getGroupId());
        return BeanUtil.copyProperties(device, BizDeviceResp.class);
    }

    @Override
    public void delete(List<Long> ids) {
        accessService.assertDevicesAllowed(ids);
        super.delete(ids);
    }

    @Override
    public void beforeCreate(BizDeviceReq req) {
        this.fillDefaults(req);
        BizPortDO port = this.checkPort(req.getPortId());
        this.checkSlaveAddrRepeat(req.getPortId(), req.getSlaveAddr(), null);
        this.checkProtocolConsistent(req.getPortId(), req.getProtocol(), null);
        this.checkGroup(req.getGroupId());
        this.checkPortDeviceLimit(port, null);
    }

    @Override
    public void beforeUpdate(BizDeviceReq req, Long id) {
        BizDeviceDO oldDevice = super.getById(id);
        BizPortDO port = this.checkPort(req.getPortId());
        this.checkSlaveAddrRepeat(req.getPortId(), req.getSlaveAddr(), id);
        this.checkProtocolConsistent(req.getPortId(), req.getProtocol(), id);
        this.checkGroup(req.getGroupId() == null ? DEFAULT_GROUP_ID : req.getGroupId());
        // 仅当设备被移动到其他端口时才校验目标端口的接入上限
        if (oldDevice == null || !ObjectUtil.equal(oldDevice.getPortId(), req.getPortId())) {
            this.checkPortDeviceLimit(port, id);
        }
    }

    @Override
    public void beforeDelete(List<Long> ids) {
        // 级联逻辑删除设备下的通道配置，避免残留孤立数据
        bizChannelMapper.delete(new LambdaQueryWrapper<BizChannelDO>()
            .in(BizChannelDO::getDeviceId, ids));
    }

    /**
     * 为未填写的参数补默认值
     *
     * @param req 请求参数
     */
    private void fillDefaults(BizDeviceReq req) {
        if (ObjectUtil.isEmpty(req.getProtocol())) {
            req.setProtocol(DEFAULT_PROTOCOL);
        }
        if (req.getTimeoutMs() == null) {
            req.setTimeoutMs(DEFAULT_TIMEOUT_MS);
        }
        if (req.getSortNo() == null) {
            req.setSortNo(DEFAULT_SORT_NO);
        }
        if (req.getGroupId() == null) {
            req.setGroupId(DEFAULT_GROUP_ID);
        }
    }

    /** 校验所属分组存在。 */
    private void checkGroup(Long groupId) {
        BizDeviceGroupDO group = bizDeviceGroupMapper.selectById(groupId);
        CheckUtils.throwIf(
            group == null || (group.getDeleted() != null && group.getDeleted() != 0L), "所属分组不存在");
    }

    /**
     * 校验所属端口存在
     *
     * @param portId 端口 ID
     * @return 端口实体
     */
    private BizPortDO checkPort(Long portId) {
        BizPortDO port = bizPortMapper.selectById(portId);
        CheckUtils.throwIf(port == null, "所属端口不存在");
        return port;
    }

    /**
     * 校验同一端口内从站地址不重复
     *
     * @param portId    端口 ID
     * @param slaveAddr 从站地址
     * @param excludeId 需排除的 ID（修改时传自身 ID）
     */
    private void checkSlaveAddrRepeat(Long portId, Integer slaveAddr, Long excludeId) {
        CheckUtils.throwIf(baseMapper.lambdaQuery()
            .eq(BizDeviceDO::getPortId, portId)
            .eq(BizDeviceDO::getSlaveAddr, slaveAddr)
            .ne(excludeId != null, BizDeviceDO::getId, excludeId)
            .exists(), "该端口下从站地址 [{}] 已被占用", slaveAddr);
    }

    /**
     * 校验同一端口下的设备协议一致（FR-PROTO-05）
     *
     * <p>端口会话的协议实例按「该端口第一台设备」创建，而一条物理链路（一串口/一总线）
     * 只能有一种 Modbus 编码：允许混用会让后建那台设备的报文编码错乱，
     * 现场表现为「一直通讯失败」且很难定位，因此在配置阶段就拦住。</p>
     *
     * <p>比较范围只含已启用设备：停用设备不参与采集，允许「先停旧协议设备、再建新协议设备」
     * 完成端口协议切换。</p>
     *
     * @param portId    端口 ID
     * @param protocol  本次要保存的协议
     * @param excludeId 需排除的 ID（修改时传自身 ID）
     */
    private void checkProtocolConsistent(Long portId, String protocol, Long excludeId) {
        if (ObjectUtil.isEmpty(protocol)) {
            return;
        }
        // 只比较「已启用」的设备：停用的设备不参与采集，也允许运维用「先停旧、再建新」
        // 的方式把一个端口从 RTU 切换到 ASCII（否则永远换不了协议）
        BizDeviceDO other = baseMapper.lambdaQuery()
            .eq(BizDeviceDO::getPortId, portId)
            .eq(BizDeviceDO::getEnabled, true)
            .ne(BizDeviceDO::getProtocol, protocol)
            .ne(excludeId != null, BizDeviceDO::getId, excludeId)
            .last("LIMIT 1")
            .one();
        String otherProtocol = other == null ? "" : other.getProtocol();
        String otherName = other == null ? "" : other.getName();
        CheckUtils.throwIf(other != null, "该端口下已有 {} 协议设备（{}）：同一条链路只能有一种编码，"
            + "请为该协议单独建一个端口", otherProtocol, otherName);
    }

    /**
     * 校验端口下的设备数量未超过该端口配置的 485 接入上限
     *
     * @param port      端口实体
     * @param excludeId 需排除的 ID（修改时传自身 ID）
     */
    private void checkPortDeviceLimit(BizPortDO port, Long excludeId) {
        long count = baseMapper.lambdaQuery()
            .eq(BizDeviceDO::getPortId, port.getId())
            .ne(excludeId != null, BizDeviceDO::getId, excludeId)
            .count();
        CheckUtils.throwIf(count >= port.getDeviceLimit(),
            "端口 [{}] 已接入 {} 台设备，达到配置上限 {}，请新增端口或调整上限",
            port.getName(), count, port.getDeviceLimit());
    }
}
