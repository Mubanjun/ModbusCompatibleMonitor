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

package top.continew.admin.monitor.runtime.report;

import com.mbj.jdcompatiblemonitor.report.ReportFormat;
import com.mbj.jdcompatiblemonitor.report.TransformSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizReportRuleMapper;
import top.continew.admin.monitor.mapper.BizReportTargetMapper;
import top.continew.admin.monitor.mapper.BizReportTemplateMapper;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizReportRuleDO;
import top.continew.admin.monitor.model.entity.BizReportTargetDO;
import top.continew.admin.monitor.model.entity.BizReportTemplateDO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上报配置缓存（FR-RPT-01~04）。
 *
 * <p>把目标、模板、规则与设备坐标装配成可执行快照；配置写入时通过 {@link #invalidate()}
 * 立即失效，采集线程只读内存。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportCatalog {

    private final BizReportTargetMapper bizReportTargetMapper;
    private final BizReportTemplateMapper bizReportTemplateMapper;
    private final BizReportRuleMapper bizReportRuleMapper;
    private final BizDeviceMapper bizDeviceMapper;
    private final ReportProperties properties;

    private volatile List<ReportRuleSpec> rules = List.of();
    private volatile Map<Long, DeviceCoordinate> coordinates = Map.of();
    private volatile Map<Long, ReportTargetSpec> targets = Map.of();
    private volatile long loadedAt;
    private volatile int version;

    /**
     * 设备坐标
     *
     * @param lng 经度
     * @param lat 纬度
     */
    public record DeviceCoordinate(Double lng, Double lat) {
    }

    /**
     * 当前启用且目标可用的上报规则
     *
     * @return 规则快照
     */
    public List<ReportRuleSpec> rules() {
        if (System.currentTimeMillis() - loadedAt < Math.max(properties.getRuleCacheMillis(),
            500)) {
            return rules;
        }
        return this.reload();
    }

    /**
     * 失效缓存，下次访问重新加载
     */
    public void invalidate() {
        loadedAt = 0L;
    }

    /**
     * 取设备坐标
     *
     * @param deviceId 设备 ID
     * @return 坐标，未配置返回空坐标
     */
    public DeviceCoordinate coordinate(long deviceId) {
        this.rules();
        return coordinates.getOrDefault(deviceId, new DeviceCoordinate(null, null));
    }

    /**
     * 按 ID 取上报目标（死信重放用）
     *
     * @param targetId 目标 ID
     * @return 目标快照，未加载到返回 null
     */
    public ReportTargetSpec target(long targetId) {
        this.rules();
        return targets.get(targetId);
    }

    /**
     * 配置版本号，内容变化时递增
     *
     * @return 版本号
     */
    public int version() {
        return version;
    }

    /**
     * 重新加载配置
     *
     * @return 规则快照
     */
    public synchronized List<ReportRuleSpec> reload() {
        List<ReportRuleSpec> loaded = new ArrayList<>();
        Map<Long, DeviceCoordinate> coords = new HashMap<>();
        Map<Long, ReportTargetSpec> targetSpecs = new HashMap<>();
        try {
            Map<Long, BizReportTargetDO> targets = new HashMap<>();
            for (BizReportTargetDO row : bizReportTargetMapper.lambdaQuery().list()) {
                targets.put(row.getId(), row);
            }
            Map<Long, BizReportTemplateDO> templates = new HashMap<>();
            for (BizReportTemplateDO row : bizReportTemplateMapper.lambdaQuery().list()) {
                templates.put(row.getId(), row);
            }
            for (BizDeviceDO device : bizDeviceMapper.lambdaQuery().list()) {
                coords.put(device.getId(), new DeviceCoordinate(device.getLongitude() == null ? null
                    : device.getLongitude().doubleValue(),
                    device.getLatitude() == null ? null
                        : device.getLatitude().doubleValue()));
            }
            for (BizReportRuleDO row : bizReportRuleMapper.lambdaQuery()
                .eq(BizReportRuleDO::getEnabled, true)
                .list()) {
                BizReportTargetDO target = targets.get(row.getTargetId());
                if (target == null || !Boolean.TRUE.equals(target.getEnabled())) {
                    continue;
                }
                BizReportTemplateDO template =
                    row.getTemplateId() == null ? null : templates.get(row
                        .getTemplateId());
                ReportTargetSpec spec = this.toTarget(target, template);
                targetSpecs.put(spec.id(), spec);
                loaded.add(new ReportRuleSpec(row.getId(), row.getName(), row.getPortId(), row
                    .getDeviceId(), row.getIntervalMs() == null ? 60_000 : row.getIntervalMs(),
                    spec));
            }
        } catch (Exception e) {
            // 失败也要推进加载时间：否则下一条采样（以及 coordinate() 的每次调用）都会立刻再进一次
            // synchronized 全表重载，数据库抖动时会在采集线程上形成查询风暴。
            // 需要立刻重试的场景由 invalidate() 把 loadedAt 置 0（配置变更路径）负责。
            loadedAt = System.currentTimeMillis();
            log.warn("加载上报配置失败，沿用上一份快照（{} 毫秒内不再重试）：{}",
                Math.max(properties.getRuleCacheMillis(), 500), e.getMessage());
            return rules;
        }
        coordinates = Map.copyOf(coords);
        targets = Map.copyOf(targetSpecs);
        if (loaded.size() != rules.size()) {
            version++;
        }
        rules = List.copyOf(loaded);
        loadedAt = System.currentTimeMillis();
        return rules;
    }

    private ReportTargetSpec toTarget(BizReportTargetDO target, BizReportTemplateDO template) {
        String content = template != null && Boolean.TRUE.equals(template.getEnabled()) ? template
            .getContent() : null;
        return new ReportTargetSpec(target.getId(), target.getName(), target.getTargetType(), target
            .getDbType(), ReportFormat.parse(target.getFormatType()),
            target.getMqttQos() == null ? 1
                : target.getMqttQos(),
            Boolean.TRUE.equals(target.getMqttRetain()), target
                .getRetryCount() == null ? 3 : target.getRetryCount(),
            target
                .getRetryIntervalMs() == null ? 5000 : target.getRetryIntervalMs(),
            target
                .getTimeoutMs() == null ? 10_000 : target.getTimeoutMs(),
            target
                .getConfigJson(),
            TransformSpec.parse(target.getConfigJson()),
            content);
    }
}
