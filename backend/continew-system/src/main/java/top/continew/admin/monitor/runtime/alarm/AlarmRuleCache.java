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

package top.continew.admin.monitor.runtime.alarm;

import com.mbj.jdcompatiblemonitor.device.ElementType;
import com.mbj.jdcompatiblemonitor.device.alarm.AlarmRuleSpec;
import com.mbj.jdcompatiblemonitor.device.alarm.AlarmRuleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.mapper.BizAlarmRuleMapper;
import top.continew.admin.monitor.model.entity.BizAlarmRuleDO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 报警规则缓存（FR-ALM-01）。
 *
 * <p>带有效期缓存启用中的规则，采集线程只读内存；内容变化时递增版本号，
 * 供 {@link AlarmEngine} 感知并清理已失效规则的报警状态。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmRuleCache {

    private final BizAlarmRuleMapper bizAlarmRuleMapper;
    private final AlarmProperties properties;

    private volatile List<AlarmRuleSpec> rules = List.of();
    private volatile Map<Long, String> notifyMethods = Map.of();
    private volatile long loadedAt;
    private volatile int version;

    /**
     * 当前启用的规则
     *
     * @return 规则快照
     */
    public List<AlarmRuleSpec> current() {
        List<AlarmRuleSpec> local = rules;
        if (System.currentTimeMillis() - loadedAt < Math.max(properties.getRuleCacheMillis(),
            1000)) {
            return local;
        }
        return this.reload();
    }

    /**
     * 强制重新加载
     *
     * @return 规则快照
     */
    public synchronized List<AlarmRuleSpec> reload() {
        List<AlarmRuleSpec> loaded = new ArrayList<>();
        Map<Long, String> methods = new HashMap<>();
        try {
            for (BizAlarmRuleDO row : bizAlarmRuleMapper.lambdaQuery()
                .eq(BizAlarmRuleDO::getEnabled, true)
                .list()) {
                AlarmRuleSpec spec = this.toSpec(row);
                if (spec == null) {
                    continue;
                }
                loaded.add(spec);
                if (row.getNotifyMethods() != null && !row.getNotifyMethods().isBlank()) {
                    methods.put(row.getId(), row.getNotifyMethods());
                }
            }
        } catch (Exception e) {
            log.warn("加载报警规则失败，沿用上一份快照：{}", e.getMessage());
            return rules;
        }
        notifyMethods = Map.copyOf(methods);
        if (!this.sameAs(loaded)) {
            version++;
            log.info("报警规则已更新：{} 条（版本 {}）", loaded.size(), version);
        }
        rules = List.copyOf(loaded);
        loadedAt = System.currentTimeMillis();
        return rules;
    }

    /**
     * 规则版本号，内容变化时递增
     *
     * @return 版本号
     */
    public int version() {
        return version;
    }

    /**
     * 取规则配置的通知方式
     *
     * @param ruleId 规则 ID
     * @return 逗号分隔的通知方式，未配置返回 null
     */
    public String notifyMethods(long ruleId) {
        return notifyMethods.get(ruleId);
    }

    /**
     * 转换为可执行规则快照
     *
     * @param row 规则实体
     * @return 规则快照，无法解析返回 null
     */
    private AlarmRuleSpec toSpec(BizAlarmRuleDO row) {
        AlarmRuleType type = AlarmRuleType.parse(row.getRuleType());
        if (type == null) {
            log.warn("报警规则 [{}] 的规则类型无法识别：{}，已跳过", row.getName(), row.getRuleType());
            return null;
        }
        String elementName = row.getName();
        if (row.getElementType() != null && !row.getElementType().isBlank()) {
            try {
                elementName = ElementType.valueOf(row.getElementType().trim()).displayName();
            } catch (IllegalArgumentException e) {
                log.debug("报警规则 [{}] 的要素类型无法识别：{}", row.getName(), row.getElementType());
            }
        }
        return new AlarmRuleSpec(row.getId(), row.getName(), row.getDeviceId(), row.getPortId(),
            row.getChannelNo(), row.getAnalogNo(), type, this.toDouble(row.getUpperLimit()),
            this.toDouble(row.getLowerLimit()), this.toDouble(row.getDeviation()),
            this.toDouble(row.getRateLimit()), row.getSwitchState(),
            row.getDurationS() == null ? 0 : row.getDurationS(),
            row.getSuppressWindowS() == null ? 0 : row.getSuppressWindowS(), elementName);
    }

    private Double toDouble(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private boolean sameAs(List<AlarmRuleSpec> other) {
        if (other.size() != rules.size()) {
            return false;
        }
        for (int i = 0; i < other.size(); i++) {
            AlarmRuleSpec left = rules.get(i);
            AlarmRuleSpec right = other.get(i);
            if (!Objects.equals(left, right)) {
                return false;
            }
        }
        return true;
    }
}
