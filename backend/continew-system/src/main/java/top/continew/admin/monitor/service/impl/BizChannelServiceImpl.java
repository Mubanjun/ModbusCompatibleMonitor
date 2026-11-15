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
import com.mbj.jdcompatiblemonitor.device.ChannelDataType;
import com.mbj.jdcompatiblemonitor.device.ElementRange;
import com.mbj.jdcompatiblemonitor.device.ElementType;
import com.mbj.jdcompatiblemonitor.protocol.ValueEncoding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizChannelMapper;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.model.entity.BizChannelDO;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.query.BizChannelQuery;
import top.continew.admin.monitor.model.req.BizChannelReq;
import top.continew.admin.monitor.model.resp.BizChannelResp;
import top.continew.admin.monitor.service.BizChannelService;
import top.continew.admin.monitor.support.ElementCatalogSupport;
import top.continew.starter.core.util.validation.CheckUtils;

import java.math.BigDecimal;

/**
 * 通道配置业务实现
 *
 * <p>要素与量程字典来自自研模块 `jdrk-device`：选择要素与量程档位后自动带出
 * 编码方式、系数 A、单位与量程，对应 SRS 的 FR-DEV-12「设备模板/字典」。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
public class BizChannelServiceImpl
    extends
    BaseServiceImpl<BizChannelMapper, BizChannelDO, BizChannelResp, BizChannelResp, BizChannelQuery, BizChannelReq>
    implements BizChannelService {

    private static final String DEFAULT_DATA_TYPE = ChannelDataType.ANALOG2_ONLY.name();
    private static final String DEFAULT_ENCODING = ValueEncoding.UINT16_SCALE_1.name();

    private final BizDeviceMapper bizDeviceMapper;

    @Override
    public void beforeCreate(BizChannelReq req) {
        this.checkDevice(req.getDeviceId());
        this.applyTemplate(req);
        this.fillDefaults(req);
        this.checkRepeat(req, null);
    }

    @Override
    public void beforeUpdate(BizChannelReq req, Long id) {
        this.checkDevice(req.getDeviceId());
        this.applyTemplate(req);
        this.fillDefaults(req);
        this.checkRepeat(req, id);
    }

    /**
     * 按要素与量程档位套用模板，带出编码方式、系数 A、单位与量程
     *
     * @param req 请求参数
     */
    private void applyTemplate(BizChannelReq req) {
        if (ObjectUtil.isEmpty(req.getElementType())) {
            return;
        }
        ElementType elementType = ElementCatalogSupport.resolveElementType(req.getElementType());
        if (ObjectUtil.isEmpty(req.getElementName())) {
            req.setElementName(elementType.displayName());
        }
        if (ObjectUtil.isEmpty(req.getUnit())) {
            req.setUnit(elementType.unit());
        }
        if (ObjectUtil.isEmpty(req.getRangeLabel())) {
            return;
        }
        ElementRange range = ElementCatalogSupport.resolveRange(elementType, req.getRangeLabel());
        req.setEncoding(range.encoding().name());
        req.setCoefficient(BigDecimal.valueOf(range.hostCoefficientA()));
        req.setMinValue(BigDecimal.valueOf(range.min()));
        req.setMaxValue(BigDecimal.valueOf(range.max()));
    }

    /**
     * 填充默认值并校验枚举取值
     *
     * @param req 请求参数
     */
    private void fillDefaults(BizChannelReq req) {
        if (ObjectUtil.isEmpty(req.getDataType())) {
            req.setDataType(DEFAULT_DATA_TYPE);
        }
        if (ObjectUtil.isEmpty(req.getEncoding())) {
            req.setEncoding(DEFAULT_ENCODING);
        }
        if (req.getRegAddr() == null) {
            req.setRegAddr(0);
        }
        if (req.getCoefficient() == null) {
            req.setCoefficient(BigDecimal.ONE);
        }
        if (req.getOffsetValue() == null) {
            req.setOffsetValue(BigDecimal.ZERO);
        }
        CheckUtils.throwIf(!ElementCatalogSupport.isValidDataType(req.getDataType()),
            "未知的通道数据属性：[{}]", req
                .getDataType());
        CheckUtils.throwIf(!ElementCatalogSupport.isValidEncoding(req.getEncoding()),
            "未知的值编码方式：[{}]", req
                .getEncoding());
    }

    /**
     * 校验所属设备存在
     *
     * @param deviceId 设备 ID
     */
    private void checkDevice(Long deviceId) {
        BizDeviceDO device = bizDeviceMapper.selectById(deviceId);
        CheckUtils.throwIf(device == null, "所属设备不存在");
    }

    /**
     * 校验同一设备下通道号与模拟量序号组合不重复
     *
     * @param req       请求参数
     * @param excludeId 需排除的 ID（修改时传自身 ID）
     */
    private void checkRepeat(BizChannelReq req, Long excludeId) {
        CheckUtils.throwIf(baseMapper.lambdaQuery()
            .eq(BizChannelDO::getDeviceId, req.getDeviceId())
            .eq(BizChannelDO::getChannelNo, req.getChannelNo())
            .eq(BizChannelDO::getAnalogNo, req.getAnalogNo())
            .ne(excludeId != null, BizChannelDO::getId, excludeId)
            .exists(), "该设备的通道 [{}] 模拟量 [{}] 已配置", req.getChannelNo(), req.getAnalogNo());
    }
}
