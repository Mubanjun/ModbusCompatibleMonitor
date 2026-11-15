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

package top.continew.admin.monitor.runtime.io;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时备份（FR-IO-05：按计划生成并可清理）。
 *
 * <p>用独立单线程调度器而不是依赖任务调度中间件：备份属于本地文件作业，
 * 不需要分布式编排，保持与采集/上报引擎一致的运行方式。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupProperties backupProperties;
    private final BackupService backupService;

    private ScheduledExecutorService executor;

    private volatile LocalDateTime lastRunAt;

    private volatile String lastResult;

    /**
     * 启动定时备份
     */
    @PostConstruct
    public void start() {
        if (!backupProperties.isAutoEnabled()) {
            log.info("定时备份未启用（jdrk.backup.auto-enabled=false）");
            return;
        }
        long hours = Math.max(1L, backupProperties.getIntervalHours());
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "jdrk-backup-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::runOnce, 1, hours * 60L, TimeUnit.MINUTES);
        log.info("定时备份已启用：每 {} 小时一次，保留 {} 份，目录 {}", hours, backupProperties.getKeep(),
            backupService.dir());
    }

    /**
     * 停止定时备份
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
     * 最近一次执行时间
     *
     * @return 时间
     */
    public LocalDateTime lastRunAt() {
        return lastRunAt;
    }

    /**
     * 最近一次执行结果
     *
     * @return 结果描述
     */
    public String lastResult() {
        return lastResult;
    }

    /**
     * 是否启用
     *
     * @return 是否启用
     */
    public boolean enabled() {
        return executor != null;
    }

    private void runOnce() {
        try {
            String password = backupProperties.getEncryptPassword();
            var file = backupService.create(password, backupProperties.isIncludeLogs());
            backupService.purge();
            lastRunAt = LocalDateTime.now();
            lastResult = "成功：" + file.getName();
            log.info("定时备份完成：{}", file.getName());
        } catch (Exception e) {
            lastRunAt = LocalDateTime.now();
            lastResult = "失败：" + e.getMessage();
            log.error("定时备份失败：{}", e.getMessage());
        }
    }
}
