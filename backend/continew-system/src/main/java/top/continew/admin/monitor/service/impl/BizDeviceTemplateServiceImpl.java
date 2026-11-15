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
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizDeviceTemplateMapper;
import top.continew.admin.monitor.model.entity.BizDeviceTemplateDO;
import top.continew.admin.monitor.model.query.BizDeviceTemplateQuery;
import top.continew.admin.monitor.model.req.BizDeviceTemplateReq;
import top.continew.admin.monitor.model.resp.BizDeviceTemplateResp;
import top.continew.admin.monitor.service.BizDeviceTemplateService;
import top.continew.admin.monitor.support.ElementCatalogSupport;
import top.continew.starter.core.util.validation.CheckUtils;

import java.math.BigDecimal;

/**
 * 设备模板业务实现
 *
 * <p>模板即「要素 + 量程档位 + 编码方式 + 系数」的命名组合，通道配置可直接引用，
 * 对应 SRS 的 FR-DEV-12。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
// @formatter:off
// 泛型参数拼接后单行会超出 Checkstyle 的 150 字符上限，而 Spotless 不会拆分泛型，故此处手工换行并豁免格式化
public class BizDeviceTemplateServiceImpl
    extends BaseServiceImpl<BizDeviceTemplateMapper, BizDeviceTemplateDO, BizDeviceTemplateResp,
        BizDeviceTemplateResp, BizDeviceTemplateQuery, BizDeviceTemplateReq>
    implements BizDeviceTemplateService {
    // @formatter:on

    private static final String DEFAULT_DATA_TYPE = ChannelDataType.ANALOG2_ONLY.name();
    private static final String DEFAULT_ENCODING = ValueEncoding.UINT16_SCALE_1.name();

    @Override
    public void beforeCreate(BizDeviceTemplateReq req) {
        this.applyTemplate(req);
        this.fillDefaults(req);
        this.checkNameRepeat(req.getName(), null);
    }

    @Override
    public void beforeUpdate(BizDeviceTemplateReq req, Long id) {
        this.applyTemplate(req);
        this.fillDefaults(req);
        this.checkNameRepeat(req.getName(), id);
    }

    /**
     * 按要素与量程档位套用模板，带出编码方式、系数 A、单位与量程
     *
     * @param req 请求参数
     */
    private void applyTemplate(BizDeviceTemplateReq req) {
        if (ObjectUtil.isEmpty(req.getElementType())) {
            return;
        }
        ElementType elementType = ElementCatalogSupport.resolveElementType(req.getElementType());
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
    private void fillDefaults(BizDeviceTemplateReq req) {
        if (ObjectUtil.isEmpty(req.getDataType())) {
            req.setDataType(DEFAULT_DATA_TYPE);
        }
        if (ObjectUtil.isEmpty(req.getEncoding())) {
            req.setEncoding(DEFAULT_ENCODING);
        }
        if (req.getCoefficient() == null) {
            req.setCoefficient(BigDecimal.ONE);
        }
        if (req.getOffsetValue() == null) {
            req.setOffsetValue(BigDecimal.ZERO);
        }
        if (req.getSort() == null) {
            req.setSort(999);
        }
        CheckUtils.throwIf(!ElementCatalogSupport.isValidDataType(req.getDataType()),
            "未知的通道数据属性：[{}]", req
                .getDataType());
        CheckUtils.throwIf(!ElementCatalogSupport.isValidEncoding(req.getEncoding()),
            "未知的值编码方式：[{}]", req
                .getEncoding());
    }

    /**
     * 检查模板名称是否重复
     *
     * @param name 模板名称
     * @param id   ID
     */
    private void checkNameRepeat(String name, Long id) {
        CheckUtils.throwIf(baseMapper.lambdaQuery()
            .eq(BizDeviceTemplateDO::getName, name)
            .ne(id != null, BizDeviceTemplateDO::getId, id)
            .exists(), "模板名称 [{}] 已存在", name);
    }
}
