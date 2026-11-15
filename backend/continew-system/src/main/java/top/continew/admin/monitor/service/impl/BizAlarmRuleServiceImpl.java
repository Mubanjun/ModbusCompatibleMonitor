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
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizAlarmRuleMapper;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.model.entity.BizAlarmRuleDO;
import top.continew.admin.monitor.model.query.BizAlarmRuleQuery;
import top.continew.admin.monitor.model.req.BizAlarmRuleReq;
import top.continew.admin.monitor.model.resp.BizAlarmRuleResp;
import top.continew.admin.monitor.runtime.alarm.AlarmRuleCache;
import top.continew.admin.monitor.service.BizAlarmRuleService;
import top.continew.admin.monitor.support.ElementCatalogSupport;
import top.continew.starter.core.util.validation.CheckUtils;

import java.util.List;
import java.util.Set;

/**
 * 报警规则业务实现
 *
 * <p>按规则类型校验必要字段（FR-ALM-01）：阈值需上下限至少其一、偏差需允许偏差、
 * 变化率需变化率上限、开关量需目标状态；同时校验要素取值、设备/端口引用与通知方式。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
public class BizAlarmRuleServiceImpl
    extends
    BaseServiceImpl<BizAlarmRuleMapper, BizAlarmRuleDO, BizAlarmRuleResp, BizAlarmRuleResp, BizAlarmRuleQuery, BizAlarmRuleReq>
    implements BizAlarmRuleService {

    private static final Set<String> NOTIFY_TYPES = Set.of("MQTT", "EMAIL", "SMS");

    private final BizDeviceMapper bizDeviceMapper;
    private final BizPortMapper bizPortMapper;
    private final AlarmRuleCache alarmRuleCache;

    @Override
    public Long create(BizAlarmRuleReq req) {
        Long id = super.create(req);
        // 规则变更立即生效，不必等缓存过期（FR-ALM-01 阈值与使能可配）
        alarmRuleCache.reload();
        return id;
    }

    @Override
    public void update(BizAlarmRuleReq req, Long id) {
        super.update(req, id);
        alarmRuleCache.reload();
    }

    @Override
    public void delete(List<Long> ids) {
        super.delete(ids);
        alarmRuleCache.reload();
    }

    @Override
    public void beforeCreate(BizAlarmRuleReq req) {
        this.fillDefaults(req);
        this.checkRefs(req);
        this.checkRuleFields(req);
        this.checkNotifyMethods(req.getNotifyMethods());
        this.checkNameRepeat(req.getName(), null);
    }

    @Override
    public void beforeUpdate(BizAlarmRuleReq req, Long id) {
        this.fillDefaults(req);
        this.checkRefs(req);
        this.checkRuleFields(req);
        this.checkNotifyMethods(req.getNotifyMethods());
        this.checkNameRepeat(req.getName(), id);
    }

    /**
     * 填充默认值
     *
     * @param req 请求参数
     */
    private void fillDefaults(BizAlarmRuleReq req) {
        if (req.getDurationS() == null) {
            req.setDurationS(0);
        }
        if (req.getSuppressWindowS() == null) {
            req.setSuppressWindowS(0);
        }
    }

    /**
     * 校验要素、设备与端口引用
     *
     * @param req 请求参数
     */
    private void checkRefs(BizAlarmRuleReq req) {
        if (ObjectUtil.isNotEmpty(req.getElementType())) {
            ElementCatalogSupport.resolveElementType(req.getElementType());
        }
        if (req.getDeviceId() != null) {
            CheckUtils.throwIf(bizDeviceMapper.selectById(req.getDeviceId()) == null, "所选设备不存在");
        }
        if (req.getPortId() != null) {
            CheckUtils.throwIf(bizPortMapper.selectById(req.getPortId()) == null, "所选端口不存在");
        }
    }

    /**
     * 按规则类型校验必要字段
     *
     * @param req 请求参数
     */
    private void checkRuleFields(BizAlarmRuleReq req) {
        String ruleType = req.getRuleType();
        if ("RANGE".equals(ruleType)) {
            CheckUtils.throwIf(req.getUpperLimit() == null && req.getLowerLimit() == null,
                "阈值报警至少需要配置上限或下限");
        } else if ("DEVIATION".equals(ruleType)) {
            CheckUtils.throwIf(req.getDeviation() == null, "偏差报警需要配置允许偏差");
        } else if ("RATE".equals(ruleType)) {
            CheckUtils.throwIf(req.getRateLimit() == null, "变化率报警需要配置变化率上限");
        } else if ("SWITCH".equals(ruleType)) {
            CheckUtils.throwIf(req.getSwitchState() == null, "开关量报警需要配置目标状态");
        }
    }

    /**
     * 校验通知方式取值
     *
     * @param notifyMethods 逗号分隔的通知方式
     */
    private void checkNotifyMethods(String notifyMethods) {
        if (ObjectUtil.isEmpty(notifyMethods)) {
            return;
        }
        for (String method : StrUtil.splitTrim(notifyMethods, ',')) {
            CheckUtils.throwIf(!NOTIFY_TYPES.contains(method.toUpperCase()), "未知的通知方式：[{}]",
                method);
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
            .eq(BizAlarmRuleDO::getName, name)
            .ne(id != null, BizAlarmRuleDO::getId, id)
            .exists(), "规则名称 [{}] 已存在", name);
    }
}
