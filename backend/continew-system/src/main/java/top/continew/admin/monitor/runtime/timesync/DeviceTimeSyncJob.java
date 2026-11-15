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

package top.continew.admin.monitor.runtime.timesync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.monitor.runtime.model.TimeSyncResultResp;

import java.util.List;

/**
 * 自动定时校时任务（NFR-REL-06）。
 *
 * <p>计划来自配置项 {@code jdrk.time-sync.auto-cron}（默认每天 03:00），
 * 开关由 SYSTEM 配置 {@code TIME_SYNC_AUTO_ENABLED} 运行时控制，偏差在容差内的
 * 设备会被服务跳过写入。</p>
 *
 * <p>这里直接复用 Spring 内置调度（启动类已开启 {@code @EnableScheduling}），
 * 而不额外引入分布式调度：现场部署只运行监控服务本身，没有 SnailJob 调度中心，
 * 本机调度是现场唯一可靠且不增加部署负担的路径。</p>
 *
 * @author jdrk
 * @since 2026/10/1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTimeSyncJob {

    private final DeviceTimeSyncService deviceTimeSyncService;

    /**
     * 自动定时校时
     */
    @Scheduled(cron = "${jdrk.time-sync.auto-cron:0 0 3 * * ?}")
    public void syncDeviceTimeWithSchedule() {
        if (!deviceTimeSyncService.autoEnabled()) {
            return;
        }
        // 调度线程不属于任何人工会话：清掉可能被继承的用户上下文
        UserContextHolder.clearContext();
        try {
            List<TimeSyncResultResp> results = deviceTimeSyncService.syncAutomatically();
            log.debug("自动定时校时完成，处理设备 {} 台", results.size());
        } catch (Exception e) {
            // 定时任务不允许把异常抛给调度线程，否则后续轮次会被框架抑制
            log.warn("自动定时校时执行失败：{}", e.getMessage());
        }
    }
}
