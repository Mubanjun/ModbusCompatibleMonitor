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
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.mapper.BizReportRuleMapper;
import top.continew.admin.monitor.mapper.BizReportTargetMapper;
import top.continew.admin.monitor.mapper.BizReportTemplateMapper;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizReportRuleDO;
import top.continew.admin.monitor.model.query.BizReportRuleQuery;
import top.continew.admin.monitor.model.req.BizReportRuleReq;
import top.continew.admin.monitor.model.resp.BizReportRuleResp;
import top.continew.admin.monitor.runtime.report.ReportCatalog;
import top.continew.admin.monitor.service.BizReportRuleService;
import top.continew.starter.core.util.validation.CheckUtils;

/**
 * 上报规则业务实现
 *
 * <p>上报规则把「目标 + 模板 + 范围 + 频率」串起来（FR-RPT-04），因此需要校验
 * 目标与模板引用是否存在；若同时限定了端口与设备，还要求设备确实属于该端口。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
public class BizReportRuleServiceImpl
    extends
    BaseServiceImpl<BizReportRuleMapper, BizReportRuleDO, BizReportRuleResp, BizReportRuleResp, BizReportRuleQuery, BizReportRuleReq>
    implements BizReportRuleService {

    private static final int DEFAULT_INTERVAL_MS = 60000;

    private final BizReportTargetMapper bizReportTargetMapper;
    private final BizReportTemplateMapper bizReportTemplateMapper;
    private final BizPortMapper bizPortMapper;
    private final BizDeviceMapper bizDeviceMapper;

    private final ReportCatalog reportCatalog;

    @Override
    public Long create(BizReportRuleReq req) {
        Long id = super.create(req);
        // 上报配置写入后立即失效缓存，避免等待过期（FR-RPT-01/04）
        reportCatalog.invalidate();
        return id;
    }

    @Override
    public void update(BizReportRuleReq req, Long id) {
        super.update(req, id);
        reportCatalog.invalidate();
    }

    @Override
    public void delete(java.util.List<Long> ids) {
        super.delete(ids);
        reportCatalog.invalidate();
    }

    @Override
    public void beforeCreate(BizReportRuleReq req) {
        this.fillDefaults(req);
        this.checkRefs(req);
        this.checkNameRepeat(req.getName(), null);
    }

    @Override
    public void beforeUpdate(BizReportRuleReq req, Long id) {
        this.fillDefaults(req);
        this.checkRefs(req);
        this.checkNameRepeat(req.getName(), id);
    }

    /**
     * 填充默认值
     *
     * @param req 请求参数
     */
    private void fillDefaults(BizReportRuleReq req) {
        if (req.getIntervalMs() == null) {
            req.setIntervalMs(DEFAULT_INTERVAL_MS);
        }
    }

    /**
     * 校验目标、模板、端口与设备的引用
     *
     * @param req 请求参数
     */
    private void checkRefs(BizReportRuleReq req) {
        CheckUtils.throwIf(bizReportTargetMapper.selectById(req.getTargetId()) == null,
            "所选上报目标不存在");
        if (req.getTemplateId() != null) {
            CheckUtils.throwIf(bizReportTemplateMapper.selectById(req.getTemplateId()) == null,
                "所选上报模板不存在");
        }
        if (req.getPortId() != null) {
            CheckUtils.throwIf(bizPortMapper.selectById(req.getPortId()) == null, "所选端口不存在");
        }
        if (req.getDeviceId() != null) {
            BizDeviceDO device = bizDeviceMapper.selectById(req.getDeviceId());
            CheckUtils.throwIf(device == null, "所选设备不存在");
            // 同时限定端口与设备时，设备必须属于该端口
            if (req.getPortId() != null) {
                CheckUtils.throwIf(!ObjectUtil.equal(device.getPortId(), req.getPortId()),
                    "所选设备不属于该端口");
            }
        }
    }

    /**
     * 检查规则名称是否重复
     *
     * @param name 规则名称
     * @param id   ID
     */
    private void checkNameRepeat(String name, Long id) {
        CheckUtils.throwIf(baseMapper.lambdaQuery()
            .eq(BizReportRuleDO::getName, name)
            .ne(id != null, BizReportRuleDO::getId, id)
            .exists(), "规则名称 [{}] 已存在", name);
    }
}
