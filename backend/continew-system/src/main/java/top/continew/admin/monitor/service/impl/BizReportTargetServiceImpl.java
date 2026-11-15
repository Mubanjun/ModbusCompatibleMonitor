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
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizReportTargetMapper;
import top.continew.admin.monitor.model.entity.BizReportTargetDO;
import top.continew.admin.monitor.model.query.BizReportTargetQuery;
import top.continew.admin.monitor.model.req.BizReportTargetReq;
import top.continew.admin.monitor.model.resp.BizReportTargetResp;
import top.continew.admin.monitor.runtime.report.MqttTopics;
import top.continew.admin.monitor.runtime.report.ReportCatalog;
import top.continew.admin.monitor.service.BizReportTargetService;
import top.continew.starter.core.util.validation.CheckUtils;

import java.util.Set;

/**
 * 上报目标业务实现
 *
 * <p>按目标类型校验连接参数（FR-RPT-01/03）：连接参数必须是合法 JSON；
 * 目标类型为数据库时必须指定受支持的方言。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
// @formatter:off
// 泛型参数拼接后单行会超出 Checkstyle 的 150 字符上限，而 Spotless 不会拆分泛型，故此处手工换行并豁免格式化
public class BizReportTargetServiceImpl
    extends BaseServiceImpl<BizReportTargetMapper, BizReportTargetDO, BizReportTargetResp,
        BizReportTargetResp, BizReportTargetQuery, BizReportTargetReq>
    implements BizReportTargetService {
    // @formatter:on

    private static final String TYPE_MQTT = "MQTT";
    private static final String TYPE_DB = "DB";
    private static final Set<String> DB_TYPES =
        Set.of("MYSQL", "MARIADB", "POSTGRESQL", "SQLSERVER", "KINGBASE",
            "ORACLE", "DM");

    private final ReportCatalog reportCatalog;

    @Override
    public Long create(BizReportTargetReq req) {
        Long id = super.create(req);
        // 上报配置写入后立即失效缓存，避免等待过期（FR-RPT-01/04）
        reportCatalog.invalidate();
        return id;
    }

    @Override
    public void update(BizReportTargetReq req, Long id) {
        super.update(req, id);
        reportCatalog.invalidate();
    }

    @Override
    public void delete(java.util.List<Long> ids) {
        super.delete(ids);
        reportCatalog.invalidate();
    }

    @Override
    public void beforeCreate(BizReportTargetReq req) {
        this.fillDefaults(req);
        this.checkConfig(req);
        this.checkNameRepeat(req.getName(), null);
    }

    @Override
    public void beforeUpdate(BizReportTargetReq req, Long id) {
        this.fillDefaults(req);
        this.checkConfig(req);
        this.checkNameRepeat(req.getName(), id);
    }

    /**
     * 填充默认值
     *
     * @param req 请求参数
     */
    private void fillDefaults(BizReportTargetReq req) {
        if (ObjectUtil.isEmpty(req.getFormatType())) {
            req.setFormatType("JSON");
        }
        if (req.getMqttRetain() == null) {
            req.setMqttRetain(false);
        }
        if (req.getRetryCount() == null) {
            req.setRetryCount(3);
        }
        if (req.getRetryIntervalMs() == null) {
            req.setRetryIntervalMs(5000);
        }
        if (req.getTimeoutMs() == null) {
            req.setTimeoutMs(10000);
        }
        if (req.getSort() == null) {
            req.setSort(999);
        }
    }

    /**
     * 按目标类型校验连接参数与方言
     *
     * @param req 请求参数
     */
    private void checkConfig(BizReportTargetReq req) {
        CheckUtils.throwIf(!JSONUtil.isTypeJSON(req.getConfigJson()), "连接参数必须是合法的 JSON");
        if (TYPE_DB.equals(req.getTargetType())) {
            CheckUtils.throwIf(ObjectUtil.isEmpty(req.getDbType()), "目标类型为数据库时必须指定数据库方言");
            CheckUtils.throwIf(!DB_TYPES.contains(req.getDbType().toUpperCase()), "不支持的数据库方言：[{}]",
                req
                    .getDbType());
        } else if (ObjectUtil.isNotEmpty(req.getDbType())) {
            CheckUtils.throwIf(true, "仅数据库目标需要指定数据库方言");
        }
        if (!TYPE_MQTT.equals(req.getTargetType()) && req.getMqttQos() != null) {
            CheckUtils.throwIf(true, "仅 MQTT 目标需要配置 QoS");
        }
        if (TYPE_MQTT.equals(req.getTargetType())) {
            this.checkMqttTopic(req.getConfigJson());
        }
    }

    /**
     * 校验 MQTT 目标的主题模板（缺陷 61）
     *
     * <p>{@code ${变量}} 占位符允许；模板为空或含字面通配符 {@code #} / {@code +} 属于配置错误，
     * 必须挡在保存阶段——否则现场要等到「报文全进死信」才会发现通道不可用。</p>
     *
     * @param configJson 连接参数 JSON
     */
    private void checkMqttTopic(String configJson) {
        String template = JSONUtil.parseObj(configJson).getStr("topic", MqttTopics.DEFAULT_TOPIC);
        try {
            MqttTopics.validateTemplate(template);
        } catch (IllegalArgumentException e) {
            CheckUtils.throwIf(true, e.getMessage());
        }
    }

    /**
     * 检查目标名称是否重复
     *
     * @param name 目标名称
     * @param id   ID
     */
    private void checkNameRepeat(String name, Long id) {
        CheckUtils.throwIf(baseMapper.lambdaQuery()
            .eq(BizReportTargetDO::getName, name)
            .ne(id != null, BizReportTargetDO::getId, id)
            .exists(), "目标名称 [{}] 已存在", name);
    }
}
