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

package top.continew.admin.monitor.runtime.cluster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.runtime.buffer.StagingBufferService;
import top.continew.admin.monitor.runtime.engine.AcquisitionEngine;
import top.continew.admin.monitor.runtime.report.ReportEngine;

/**
 * 集群角色与引擎生命周期协调（NFR-SCALE-04）
 *
 * <p>主节点启动采集/上报/暂存；降为从节点时停掉这三者，只保留查询与配置能力。
 * 每个引擎自己的 ApplicationReadyEvent 里也会判断角色，这里只负责「角色变化之后」的动作，
 * 两边都是幂等的（start/stop 可重复调用）。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterCoordinator {

    private final ClusterProperties properties;
    private final ClusterLeaderService leaderService;
    private final ObjectProvider<AcquisitionEngine> acquisitionEngine;
    private final ObjectProvider<StagingBufferService> stagingBufferService;
    private final ObjectProvider<ReportEngine> reportEngine;

    /**
     * 应用就绪：集群模式下由本组件决定引擎是否启动（单机模式各引擎自行启动）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("集群模式运行中：实例 {} 角色 {}", leaderService.instanceId(), leaderService.role());
        if (leaderService.isLeader()) {
            this.startEngines("启动即为租约持有者");
        } else {
            log.info("本实例为从节点：不启动采集/上报/暂存，仅提供查询与配置");
        }
    }

    /**
     * 角色变化：主节点启动引擎，从节点停止引擎
     *
     * @param event 角色变化事件
     */
    @EventListener
    public void onRoleChanged(ClusterRoleChangedEvent event) {
        if (event.leader()) {
            this.startEngines(event.reason());
        } else {
            this.stopEngines(event.reason());
        }
    }

    private void startEngines(String reason) {
        StagingBufferService staging = stagingBufferService.getIfAvailable();
        ReportEngine report = reportEngine.getIfAvailable();
        AcquisitionEngine acquisition = acquisitionEngine.getIfAvailable();
        if (staging != null) {
            staging.start();
        }
        if (report != null) {
            report.start();
        }
        if (acquisition != null) {
            acquisition.start();
        }
        log.info("主节点引擎已就绪（{}）：暂存/上报/采集均已启动", reason);
    }

    private void stopEngines(String reason) {
        AcquisitionEngine acquisition = acquisitionEngine.getIfAvailable();
        ReportEngine report = reportEngine.getIfAvailable();
        StagingBufferService staging = stagingBufferService.getIfAvailable();
        if (acquisition != null) {
            acquisition.stop();
        }
        if (report != null) {
            report.stop();
        }
        if (staging != null) {
            staging.stop();
        }
        log.warn("已停止本实例的采集/上报/暂存（{}）：本实例降为只读角色", reason);
    }
}
