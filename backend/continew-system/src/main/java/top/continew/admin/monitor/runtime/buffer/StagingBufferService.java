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

package top.continew.admin.monitor.runtime.buffer;

import com.mbj.jdcompatiblemonitor.storage.StagingRecord;
import com.mbj.jdcompatiblemonitor.storage.StagingStats;
import com.mbj.jdcompatiblemonitor.storage.StagingStore;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.model.entity.BizDataDO;
import top.continew.admin.monitor.model.entity.BizDataLatestDO;
import top.continew.admin.monitor.runtime.alarm.AlarmStateRegistry;
import top.continew.admin.monitor.runtime.cluster.ClusterLeaderService;
import top.continew.admin.monitor.runtime.cluster.ClusterProperties;
import top.continew.admin.monitor.runtime.model.StagingStatusResp;
import top.continew.admin.monitor.runtime.service.RuntimeSettingsService;
import top.continew.admin.monitor.service.BizDataLatestService;
import top.continew.admin.monitor.service.BizDataService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 暂存缓冲与数据落地（FR-BUF-02/03/04/05/06、FR-WEB-01/03/05）。
 *
 * <p>周期内完成四件事：把内存样本落入暂存 → 同步设备状态 → 取一批暂存上传到业务库
 * （历史表幂等写入 + 实时表 upsert）→ 按策略滚动清理。</p>
 *
 * <p>业务库不可用时批次整体退回待上传，下一周期按写入顺序重试，因此断网期间数据只积压不丢失
 * （FR-BUF-03 断网续传）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StagingBufferService {

    private final StagingProperties properties;
    private final StagingStore stagingStore;
    private final PersistenceReadingSink readingSink;
    private final BizDataService bizDataService;
    private final BizDataLatestService bizDataLatestService;
    private final RuntimeSettingsService runtimeSettingsService;
    private final AlarmStateRegistry alarmStateRegistry;
    private final DiskSpaceGuard diskSpaceGuard;
    private final ClusterProperties clusterProperties;
    private final ClusterLeaderService clusterLeaderService;

    private final AtomicLong uploadedBatches = new AtomicLong(0L);
    private final AtomicLong uploadedRows = new AtomicLong(0L);
    private final AtomicLong failedBatches = new AtomicLong(0L);
    private final AtomicLong purgedHistoryRows = new AtomicLong(0L);

    private volatile long lastErrorTime;
    private volatile String lastError;
    private volatile long lastPurgeTime;
    private volatile ScheduledExecutorService scheduler;

    /** 磁盘保护模式：低于保护阈值时收紧本地暂存上限，避免写满磁盘（NFR-FAULT-03） */
    private volatile boolean protectionMode;

    /** 保护模式下的暂存行数上限，0 表示未生效 */
    private volatile long protectionCap;

    /**
     * 应用就绪后启动落地线程
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (clusterProperties.isEnabled() && !clusterLeaderService.isLeader()) {
            log.info("集群模式：本实例为从节点，本地暂存落地不启动（由主节点写入业务库）");
            return;
        }
        if (scheduler != null) {
            return;
        }
        int interval = Math.max(properties.getFlushIntervalMs(), 200);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "acq-staging-flusher");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::cycle, interval, interval, TimeUnit.MILLISECONDS);
        this.scheduler = executor;
        log.info("暂存落地已启动：周期 {}ms，批量 {}，容量上限 {}", interval, properties.getBatchSize(),
            runtimeSettingsService.bufferMaxRows(properties.getMaxRows()));
    }

    /**
     * 停止落地线程
     */
    @PreDestroy
    public void stop() {
        ScheduledExecutorService executor = scheduler;
        scheduler = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        // 退出前尽力把内存样本刷入暂存，避免进程重启丢数据
        try {
            readingSink.drainToStore();
        } catch (Exception e) {
            log.warn("退出前刷入暂存失败：{}", e.getMessage());
        }
    }

    /**
     * 立即执行一次落地周期（手动触发与测试用）
     */
    public synchronized void cycle() {
        try {
            readingSink.drainToStore();
            // 顺序很重要：先落样本（toLatestDO 会把有新数据的设备写成 ONLINE），再判离线。
            // 反过来的话，刚落盘的批次会把刚标记的 OFFLINE/COMM_ERROR 整列覆盖回 ONLINE；
            // 而且落盘批次可能滞后（每周期只搬 batchSize 条），迟到的旧样本会"复活"已经离线的设备。
            this.drainStore();
            this.syncDeviceStates();
            this.purgeIfDue();
        } catch (Exception e) {
            lastError = e.getMessage();
            lastErrorTime = System.currentTimeMillis();
            log.error("暂存落地周期异常：{}", e.getMessage(), e);
        }
        // 每个落地周期（默认 1 秒）都评估一次磁盘空间，保护模式不必等下一次心跳
        this.applyDiskGuard();
    }

    /**
     * 单个周期内连续上传多批，直到暂存清空或达到本周期的批次数上限
     *
     * <p>审计发现：原来每周期只调一次 {@code uploadBatch()}，即 500 行/秒封顶；
     * 满配采集矩阵（单轮 3 万+ 采样）下必然无界积压，只能靠容量上限丢弃数据。
     * 改为按 {@code maxBatchesPerCycle}（默认 20，即 1 万行/周期）追赶，
     * 仍保留「本轮上限」避免单周期长时间占用落地线程。</p>
     */
    private void drainStore() {
        int rounds = Math.max(properties.getMaxBatchesPerCycle(), 1);
        for (int index = 0; index < rounds; index++) {
            if (stagingStore.stats().pendingCount() <= 0L) {
                return;
            }
            if (!this.uploadBatch()) {
                // 上传失败（例如业务库不可用）：本周期不再重试，退回下一周期，避免空转刷日志
                return;
            }
        }
    }

    /**
     * 按磁盘剩余空间维护保护模式（NFR-FAULT-03）
     *
     * <p>低于保护阈值即收紧本地暂存上限；恢复后自动退出，避免一次抖动长期卡在保护模式。</p>
     */
    private void applyDiskGuard() {
        boolean protect = diskSpaceGuard.inProtection();
        if (protect && !protectionMode) {
            protectionMode = true;
            protectionCap = Math.max(properties.getBatchSize(), 500);
            log.error("磁盘剩余空间不足，进入保护模式：本地暂存上限临时收紧到 {} 行（{}）", protectionCap,
                diskSpaceGuard.describe());
        } else if (!protect && protectionMode) {
            protectionMode = false;
            protectionCap = 0L;
            log.info("磁盘剩余空间恢复，已退出保护模式（{}）", diskSpaceGuard.describe());
        }
    }

    /**
     * 暂存与落地状态快照（FR-BUF-06）
     *
     * @return 状态快照
     */
    public StagingStatusResp status() {
        StagingStats stats = stagingStore.stats();
        StagingStatusResp resp = new StagingStatusResp();
        resp.setPendingCount(stats.pendingCount());
        resp.setInFlightCount(stats.inFlightCount());
        resp.setBacklog(stats.backlog());
        resp.setOldestCollectTime(this.toMillis(stats.oldestCollectTime()));
        resp.setNewestCollectTime(this.toMillis(stats.newestCollectTime()));
        resp.setAppendedCount(stats.appendedCount());
        resp.setFlushedCount(stats.flushedCount());
        resp.setDroppedCount(stats.droppedCount());
        resp.setFileSizeBytes(stats.fileSizeBytes());
        resp.setQueuedCount(readingSink.queuedCount());
        resp.setQueuedDroppedCount(readingSink.droppedCount());
        resp.setUploadedBatches(uploadedBatches.get());
        resp.setUploadedRows(uploadedRows.get());
        resp.setFailedBatches(failedBatches.get());
        resp.setPurgedHistoryRows(purgedHistoryRows.get());
        // 保护模式下把收紧后的上限也暴露出来，便于现场确认「空间保护已生效」
        long configuredCap = runtimeSettingsService.bufferMaxRows(properties.getMaxRows());
        resp.setBufferMaxRows(
            protectionCap > 0L ? Math.min(protectionCap, configuredCap) : configuredCap);
        resp.setRetainDays(runtimeSettingsService.historyRetainDays(properties.getRetainDays()));
        resp.setFlushIntervalMs(properties.getFlushIntervalMs());
        resp.setBatchSize(properties.getBatchSize());
        resp.setDatabasePath(properties.getDatabasePath());
        resp.setLastError(lastError);
        resp.setLastErrorTime(this.toMillis(lastErrorTime));
        resp.setFreeSpaceMb(diskSpaceGuard.freeMb());
        resp.setMinFreeMb(diskSpaceGuard.minFreeMb());
        resp.setProtectFreeMb(diskSpaceGuard.protectFreeMb());
        resp.setDiskLevel(diskSpaceGuard.level().name());
        resp.setProtectionMode(protectionMode);
        return resp;
    }

    /**
     * 把设备状态变化同步到实时值表（FR-WEB-03）
     */
    private void syncDeviceStates() {
        Map<Long, String> states = readingSink.drainStates();
        for (Map.Entry<Long, String> entry : states.entrySet()) {
            try {
                bizDataLatestService.updateDeviceStatus(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.debug("同步设备状态失败：{}", e.getMessage());
            }
        }
    }

    /**
     * 取一批暂存上传到业务库
     *
     * @return 是否可继续上传下一批（批次为空或上传失败时为 false）
     */
    private boolean uploadBatch() {
        String batchId = UUID.randomUUID().toString();
        List<StagingRecord> batch = stagingStore.claimBatch(Math.max(properties.getBatchSize(), 1),
            batchId);
        if (batch.isEmpty()) {
            return false;
        }
        try {
            List<Long> ids = new ArrayList<>(batch.size());
            List<BizDataDO> histories = new ArrayList<>(batch.size());
            List<BizDataLatestDO> latests = new ArrayList<>(batch.size());
            for (StagingRecord record : batch) {
                ids.add(record.id());
                histories.add(this.toDataDO(record));
                latests.add(this.toLatestDO(record));
            }
            int inserted = bizDataService.saveIdempotent(histories);
            bizDataLatestService.upsertAll(latests);
            stagingStore.delete(ids);
            uploadedBatches.incrementAndGet();
            uploadedRows.addAndGet(inserted);
            lastError = null;
            return true;
        } catch (Exception e) {
            stagingStore.releaseBatch(batchId);
            failedBatches.incrementAndGet();
            lastError = e.getMessage();
            lastErrorTime = System.currentTimeMillis();
            log.warn("暂存批次上传失败，已退回待重试（{} 条）：{}", batch.size(), e.getMessage());
            return false;
        }
    }

    /**
     * 到点执行滚动清理与保留期清理
     */
    private void purgeIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastPurgeTime < Math.max(properties.getPurgeIntervalMs(), 60_000)) {
            return;
        }
        lastPurgeTime = now;
        long configured = runtimeSettingsService.bufferMaxRows(properties.getMaxRows());
        // 保护模式：上限临时收紧，宁可丢弃最旧的暂存也不能把磁盘写满（NFR-FAULT-03）
        long maxRows = protectionCap > 0L ? Math.min(protectionCap, configured) : configured;
        int purged = stagingStore.purgeOverflow(maxRows);
        if (purged > 0) {
            log.warn("本地暂存超过容量上限 {}，已清理最旧的 {} 条数据", maxRows, purged);
        }
        int retainDays = runtimeSettingsService.historyRetainDays(properties.getRetainDays());
        try {
            LocalDateTime deadline = LocalDateTime.now().minusDays(retainDays);
            int deleted = bizDataService.purgeBefore(deadline);
            if (deleted > 0) {
                purgedHistoryRows.addAndGet(deleted);
                log.info("已清理 {} 条超过 {} 天保留期的历史数据", deleted, retainDays);
            }
        } catch (Exception e) {
            log.warn("清理过期历史数据失败：{}", e.getMessage());
        }
    }

    /**
     * 磁盘空间不足时的保护动作（NFR-FAULT-03）
     *
     * <p>保护模式做两件事：把本地暂存上限临时收紧到一批的量级（配合滚动清理保证本地队列有界），
     * 以及立即跑一次落地周期尽量把已落盘数据推送到业务库、腾出空间。</p>
     *
     * @return 处理说明
     */
    public String relieveDiskPressure() {
        this.applyDiskGuard();
        try {
            this.cycle();
        } catch (Exception e) {
            log.warn("保护模式下的落地周期异常：{}", e.getMessage());
        }
        return "已进入保护模式（暂存上限 " + protectionCap + " 行）";
    }

    /**
     * 磁盘空间恢复后退出保护模式
     */
    public void exitProtection() {
        this.applyDiskGuard();
    }

    /**
     * 暂存记录转换为历史数据实体
     *
     * @param record 暂存记录
     * @return 历史数据实体
     */
    private BizDataDO toDataDO(StagingRecord record) {
        BizDataDO entity = new BizDataDO();
        entity.setCreateUser(0L);
        entity.setDeviceId(record.deviceId());
        entity.setDeviceName(record.deviceName());
        entity.setDeviceAddr(record.deviceAddr());
        entity.setNodeId(record.channelNo());
        entity.setAnalogNo(record.analogNo());
        entity.setElementType(record.elementType());
        entity.setElementName(record.elementName());
        entity.setDataValue(record.engineeringValue());
        entity.setRawValue(record.rawValue());
        entity.setUnit(record.unit());
        entity.setAlarmFlag(alarmStateRegistry.alarmedAt(record.deviceId(), record.channelNo(),
            record.analogNo(), record.collectTime()) ? 1 : 0);
        entity.setRecordTime(this.toLocalDateTime(record.collectTime()));
        entity.setSourceId(record.id());
        return entity;
    }

    /**
     * 暂存记录转换为实时值实体
     *
     * @param record 暂存记录
     * @return 实时值实体
     */
    private BizDataLatestDO toLatestDO(StagingRecord record) {
        BizDataLatestDO entity = new BizDataLatestDO();
        entity.setCreateUser(0L);
        entity.setDeviceId(record.deviceId());
        entity.setDeviceName(record.deviceName());
        entity.setDeviceAddr(record.deviceAddr());
        entity.setPortId(record.portId());
        entity.setNodeId(record.channelNo());
        entity.setAnalogNo(record.analogNo());
        entity.setElementType(record.elementType());
        entity.setElementName(record.elementName());
        entity.setDataValue(record.engineeringValue());
        entity.setRawValue(record.rawValue());
        entity.setUnit(record.unit());
        entity.setDeviceStatus("ONLINE");
        entity.setQuality(record.quality());
        entity.setOverRange(record.overRange());
        entity.setCollectTime(this.toLocalDateTime(record.collectTime()));
        return entity;
    }

    private LocalDateTime toLocalDateTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    private Long toMillis(long millis) {
        return millis <= 0L ? null : millis;
    }
}
