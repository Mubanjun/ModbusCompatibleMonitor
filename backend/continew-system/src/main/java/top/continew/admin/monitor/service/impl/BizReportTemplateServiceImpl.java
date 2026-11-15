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
import top.continew.admin.monitor.mapper.BizReportTemplateMapper;
import top.continew.admin.monitor.model.entity.BizReportTemplateDO;
import top.continew.admin.monitor.model.query.BizReportTemplateQuery;
import top.continew.admin.monitor.model.req.BizReportTemplateReq;
import top.continew.admin.monitor.model.resp.BizReportTemplateResp;
import top.continew.admin.monitor.runtime.report.ReportCatalog;
import top.continew.admin.monitor.service.BizReportTemplateService;
import top.continew.starter.core.util.validation.CheckUtils;

/**
 * 上报模板业务实现
 *
 * <p>模板内容支持变量占位，因此这里不校验其 JSON 合法性，只保证名称与编码唯一。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
// @formatter:off
// 泛型参数拼接后单行会超出 Checkstyle 的 150 字符上限，而 Spotless 不会拆分泛型，故此处手工换行并豁免格式化
public class BizReportTemplateServiceImpl
    extends BaseServiceImpl<BizReportTemplateMapper, BizReportTemplateDO, BizReportTemplateResp,
        BizReportTemplateResp, BizReportTemplateQuery, BizReportTemplateReq>
    implements BizReportTemplateService {
    // @formatter:on

    private final ReportCatalog reportCatalog;

    @Override
    public Long create(BizReportTemplateReq req) {
        Long id = super.create(req);
        // 上报配置写入后立即失效缓存，避免等待过期（FR-RPT-01/04）
        reportCatalog.invalidate();
        return id;
    }

    @Override
    public void update(BizReportTemplateReq req, Long id) {
        super.update(req, id);
        reportCatalog.invalidate();
    }

    @Override
    public void delete(java.util.List<Long> ids) {
        super.delete(ids);
        reportCatalog.invalidate();
    }

    @Override
    public void beforeCreate(BizReportTemplateReq req) {
        this.checkNameRepeat(req.getName(), null);
        this.checkCodeRepeat(req.getCode(), null);
    }

    @Override
    public void beforeUpdate(BizReportTemplateReq req, Long id) {
        this.checkNameRepeat(req.getName(), id);
        this.checkCodeRepeat(req.getCode(), id);
    }

    /**
     * 检查模板名称是否重复
     *
     * @param name 模板名称
     * @param id   ID
     */
    private void checkNameRepeat(String name, Long id) {
        CheckUtils.throwIf(baseMapper.lambdaQuery()
            .eq(BizReportTemplateDO::getName, name)
            .ne(id != null, BizReportTemplateDO::getId, id)
            .exists(), "模板名称 [{}] 已存在", name);
    }

    /**
     * 检查模板编码是否重复
     *
     * @param code 模板编码
     * @param id   ID
     */
    private void checkCodeRepeat(String code, Long id) {
        CheckUtils.throwIf(baseMapper.lambdaQuery()
            .eq(BizReportTemplateDO::getCode, code)
            .ne(id != null, BizReportTemplateDO::getId, id)
            .exists(), "模板编码 [{}] 已存在", code);
    }
}
