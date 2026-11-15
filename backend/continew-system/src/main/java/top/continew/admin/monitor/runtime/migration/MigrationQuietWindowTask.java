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

package top.continew.admin.monitor.runtime.migration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.runtime.engine.AcquisitionEngine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 并存错峰窗口（FR-MIG-01）：COEXIST 模式下在配置的窗口内暂停本系统采集，让仁科原软件独占设备。
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigrationQuietWindowTask {

    private final MigrationProperties properties;
    private final MigrationService migrationService;
    private final AcquisitionEngine acquisitionEngine;

    private ScheduledExecutorService executor;

    private volatile boolean pausedByWindow;

    /**
     * 启动错峰窗口检查
     */
    @PostConstruct
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "jdrk-migration-window");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, 30, 60, TimeUnit.SECONDS);
    }

    /**
     * 停止检查
     */
    @PreDestroy
    public void stop() {
        ScheduledExecutorService local = executor;
        executor = null;
        if (local != null) {
            local.shutdownNow();
        }
    }

    /**
     * 是否因错峰窗口而暂停
     *
     * @return 是否暂停中
     */
    public boolean pausedByWindow() {
        return pausedByWindow;
    }

    private void tick() {
        try {
            if (!migrationService.quietWindowEnabled()) {
                if (pausedByWindow) {
                    acquisitionEngine.resume(null);
                    pausedByWindow = false;
                    log.info("错峰窗口结束，采集已恢复");
                }
                return;
            }
            boolean inWindow = migrationService.inQuietWindow();
            if (inWindow && !pausedByWindow) {
                acquisitionEngine.pause(null);
                pausedByWindow = true;
                log.warn("进入错峰共存窗口（{}~{}），本系统采集已暂停", properties.getQuietWindowStart(),
                    properties.getQuietWindowEnd());
            } else if (!inWindow && pausedByWindow) {
                acquisitionEngine.resume(null);
                pausedByWindow = false;
                log.info("离开错峰共存窗口，采集已恢复");
            }
        } catch (Exception e) {
            log.warn("错峰窗口检查失败：{}", e.getMessage());
        }
    }
}
