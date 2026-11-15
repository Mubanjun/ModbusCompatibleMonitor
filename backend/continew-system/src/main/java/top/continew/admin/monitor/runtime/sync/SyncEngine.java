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

package top.continew.admin.monitor.runtime.sync;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mbj.jdcompatiblemonitor.report.SqlDialect;
import com.mbj.jdcompatiblemonitor.sync.ConflictDecision;
import com.mbj.jdcompatiblemonitor.sync.ConflictPolicy;
import com.mbj.jdcompatiblemonitor.sync.ConflictResolver;
import com.mbj.jdcompatiblemonitor.sync.SyncRecord;
import com.mbj.jdcompatiblemonitor.sync.SyncTable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.mapper.BizReportTargetMapper;
import top.continew.admin.monitor.model.entity.BizReportTargetDO;
import top.continew.admin.monitor.model.entity.BizSyncConflictDO;
import top.continew.admin.monitor.model.entity.BizSyncTaskDO;
import top.continew.admin.monitor.model.entity.BizSyncWatermarkDO;
import top.continew.admin.monitor.runtime.model.SyncProgressResp;
import top.continew.admin.monitor.service.BizSyncConflictService;
import top.continew.admin.monitor.service.BizSyncTaskService;
import top.continew.admin.monitor.service.BizSyncWatermarkService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 同步引擎（FR-SYNC-01~06）。
 *
 * <p>按任务把本系统数据同步到仁科兼容目标库：按（记录时间, 主键）游标分页读取来源，
 * 与目标库比对后按冲突策略写入，冲突落 {@code biz_sync_conflict}，
 * 每批成功后推进 {@code biz_sync_watermark}，因此暂停/中断后可从水位续传。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncEngine {

    /** 空闲 */
    public static final String STATUS_IDLE = "IDLE";
    /** 运行中 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 已暂停 */
    public static final String STATUS_PAUSED = "PAUSED";
    /** 已完成 */
    public static final String STATUS_DONE = "DONE";
    /** 失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 全量模式 */
    private static final String MODE_FULL = "FULL";
    /** 增量模式 */
    private static final String MODE_INCREMENT = "INCREMENT";

    private static final DateTimeFormatter EARLIEST_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BizSyncTaskService bizSyncTaskService;
    private final BizSyncConflictService bizSyncConflictService;
    private final BizSyncWatermarkService bizSyncWatermarkService;
    private final BizReportTargetMapper bizReportTargetMapper;
    private final SyncSourceReader syncSourceReader;
    private final SyncTargetWriter syncTargetWriter;
    private final SyncProperties properties;

    private final Map<Long, SyncTaskState> states = new ConcurrentHashMap<>();
    private volatile ExecutorService workers;

    /**
     * 初始化工作线程池
     */
    @PostConstruct
    public void init() {
        int concurrency = Math.max(properties.getMaxConcurrentTasks(), 1);
        this.workers = Executors.newFixedThreadPool(concurrency, task -> {
            Thread thread = new Thread(task, "acq-sync-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 停止工作线程
     */
    @PreDestroy
    public void shutdown() {
        ExecutorService executor = workers;
        workers = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * 启动同步任务（新的一轮，全量从头开始、增量从水位开始）
     *
     * @param taskId 任务 ID
     * @return 提示信息
     */
    public String start(Long taskId) {
        return this.submit(taskId, false);
    }

    /**
     * 续传同步任务（从水位继续）
     *
     * @param taskId 任务 ID
     * @return 提示信息
     */
    public String resume(Long taskId) {
        return this.submit(taskId, true);
    }

    /**
     * 请求暂停同步任务
     *
     * @param taskId 任务 ID
     * @return 是否已请求
     */
    public boolean pause(Long taskId) {
        SyncTaskState state = states.get(taskId);
        if (state == null || !STATUS_RUNNING.equals(state.status())) {
            return false;
        }
        state.requestPause();
        return true;
    }

    /**
     * 查询同步进度（FR-SYNC-06）
     *
     * @param taskId 任务 ID
     * @return 进度
     */
    public SyncProgressResp progress(Long taskId) {
        BizSyncTaskDO task = bizSyncTaskService.getById(taskId);
        SyncProgressResp resp = new SyncProgressResp();
        resp.setTaskId(taskId);
        if (task == null) {
            resp.setStatus("NOT_FOUND");
            return resp;
        }
        resp.setTaskName(task.getName());
        resp.setSyncMode(task.getSyncMode());
        resp.setConflictPolicy(task.getConflictPolicy());
        resp.setErrorMsg(task.getErrorMsg());
        SyncTaskState state = states.get(taskId);
        if (state == null) {
            resp.setStatus(task.getTaskStatus() == null ? STATUS_IDLE : task.getTaskStatus());
            resp.setTotalCount(task.getTotalCount());
            resp.setSuccessCount(task.getSuccessCount());
            resp.setFailCount(task.getFailCount());
            resp.setConflictCount(bizSyncConflictService.lambdaQuery()
                .eq(BizSyncConflictDO::getTaskId, taskId)
                .count());
            resp.setSkippedCount(0L);
            return resp;
        }
        resp.setStatus(state.status());
        resp.setCurrentTable(state.currentTable());
        resp.setTotalCount(state.total().get());
        resp.setSuccessCount(state.success().get());
        resp.setConflictCount(state.conflict().get());
        resp.setSkippedCount(state.skipped().get());
        resp.setFailCount(state.fail().get());
        long start = state.startedAt();
        long end = state.finishedAt() > 0 ? state.finishedAt() : System.currentTimeMillis();
        resp.setDurationMs(start > 0 ? end - start : null);
        if (state.errorMsg() != null) {
            resp.setErrorMsg(state.errorMsg());
        }
        return resp;
    }

    /**
     * 当前运行中的任务数（供可观测接口使用）
     *
     * @return 运行中的任务数
     */
    public long runningCount() {
        return states.values().stream().filter(state -> STATUS_RUNNING.equals(state.status()))
            .count();
    }

    private String submit(Long taskId, boolean continueFromWatermark) {
        BizSyncTaskDO task = bizSyncTaskService.getById(taskId);
        if (task == null) {
            return "同步任务不存在";
        }
        if (task.getTargetId() == null) {
            return "同步任务未配置目标库";
        }
        if (!Boolean.TRUE.equals(task.getEnabled())) {
            return "任务已停用";
        }
        // 互斥必须用「原子占位 + 在提交线程里就置 RUNNING」：
        // 原来的 get 判断再 put 是 check-then-act，两次并发 /start（或排队期间再点一次）都会放行，
        // 导致同一任务跑两个实例、共享水位线互相覆盖、冲突表重复落库。
        SyncTaskState state = new SyncTaskState(taskId);
        state.status(STATUS_RUNNING);
        state.startedAt(System.currentTimeMillis());
        SyncTaskState previous = states.putIfAbsent(taskId, state);
        if (previous != null) {
            if (!previous.finished()) {
                return "任务正在运行中";
            }
            // 上一次已经跑完（DONE/FAILED/PAUSED）：替换成新的运行状态继续
            SyncTaskState replaced = states.compute(taskId,
                (key, current) -> current == previous ? state : current);
            if (replaced != state) {
                return "任务正在运行中";
            }
        }
        ExecutorService executor = workers;
        if (executor == null || executor.isShutdown()) {
            states.remove(taskId, state);
            return "同步线程池已关闭";
        }
        executor.submit(() -> this.run(taskId, state, continueFromWatermark));
        return continueFromWatermark ? "已提交续传" : "已提交同步";
    }

    private void run(Long taskId, SyncTaskState state, boolean continueFromWatermark) {
        BizSyncTaskDO task = bizSyncTaskService.getById(taskId);
        state.status(STATUS_RUNNING);
        state.startedAt(System.currentTimeMillis());
        state.finishedAt(0L);
        state.deadline(this.deadlineOf());
        try {
            SyncTargetWriter.TargetConnection connection = this.connection(task);
            List<SyncTable> tables = this.tables(task);
            if (tables.isEmpty()) {
                throw new IllegalStateException("未配置有效的同步表（可选 tbhistory / tbalarm /"
                    + " tbnoticelog / tbsyslog）");
            }
            boolean useWatermark = continueFromWatermark || MODE_INCREMENT.equalsIgnoreCase(task
                .getSyncMode());
            LocalDateTime to = LocalDateTime.now();
            for (SyncTable table : tables) {
                if (state.pauseRequested()) {
                    this.markPaused(task, state);
                    return;
                }
                this.checkTimeout(state);
                state.currentTable(table.tableName());
                BizSyncWatermarkDO mark = this.watermark(task.getTargetId(), table.tableName());
                LocalDateTime from = EARLIEST_FORMAT.parse(properties.getEarliestTime(),
                    LocalDateTime::from);
                LocalDateTime cursorTime = null;
                String cursorId = null;
                if (useWatermark && mark != null && mark.getLastRecordTime() != null) {
                    from = mark.getLastRecordTime();
                    cursorTime = mark.getLastRecordTime();
                    cursorId = mark.getLastId();
                }
                state.total().addAndGet(syncSourceReader.count(table, from, to));
                this.syncTable(task, state, connection, table, from, to, cursorTime, cursorId);
                if (state.pauseRequested()) {
                    this.markPaused(task, state);
                    return;
                }
            }
            state.status(STATUS_DONE);
            state.finishedAt(System.currentTimeMillis());
            // 收尾必须用「最新一行」而不是 run 开始时的快照：长全量同步期间运维可能已经改了冲突策略/停用了任务，
            // 用旧快照 updateById（会写全部非空字段）等于把它们静默改回去。
            // error_msg 必须显式 set(null)：MyBatis-Plus 默认 NOT_NULL 更新策略会跳过 null 字段，
            // 否则任务由 FAILED 转成功后页面会一直显示上一次的报错。
            bizSyncTaskService.lambdaUpdate()
                .eq(BizSyncTaskDO::getId, taskId)
                .set(BizSyncTaskDO::getTaskStatus, STATUS_DONE)
                .set(BizSyncTaskDO::getLastSyncTime, LocalDateTime.now())
                .set(BizSyncTaskDO::getTotalCount, state.total().get())
                .set(BizSyncTaskDO::getSuccessCount, state.success().get())
                .set(BizSyncTaskDO::getFailCount, state.fail().get())
                .set(BizSyncTaskDO::getErrorMsg, null)
                .update();
            log.info("同步任务 [{}] 完成：成功 {} 条，冲突 {} 条，已同步 {} 条", task.getName(), state
                .success()
                .get(), state.conflict().get(), state.skipped().get());
        } catch (Exception e) {
            state.status(STATUS_FAILED);
            state.finishedAt(System.currentTimeMillis());
            state.errorMsg(e.getMessage());
            BizSyncTaskDO latest = bizSyncTaskService.getById(taskId);
            if (latest != null) {
                latest.setTaskStatus(STATUS_FAILED);
                latest.setErrorMsg(this.truncate(e.getMessage()));
                bizSyncTaskService.updateById(latest);
            }
            log.error("同步任务 [{}] 失败：{}", task.getName(), e.getMessage(), e);
        }
    }

    private void syncTable(BizSyncTaskDO task, SyncTaskState state,
        SyncTargetWriter.TargetConnection connection, SyncTable table, LocalDateTime from,
        LocalDateTime to, LocalDateTime cursorTime, String cursorId) {
        LocalDateTime cursor = cursorTime;
        String lastId = cursorId;
        int batchSize = Math.max(properties.getBatchSize(), 1);
        while (true) {
            // 批边界检查运行时长：任务卡在 RUNNING 时页面无法重启、运维也看不出原因，
            // 到点直接抛出让 run() 的统一异常出口把它标成 FAILED（水位已保存，可从断点续传）。
            this.checkTimeout(state);
            List<SyncRecord> batch =
                syncSourceReader.read(table, from, to, cursor, lastId, batchSize);
            if (batch.isEmpty()) {
                return;
            }
            this.syncBatch(task, state, connection, table, batch);
            SyncRecord last = batch.get(batch.size() - 1);
            cursor = last.recordTime();
            lastId = last.id();
            this.saveWatermark(task.getTargetId(), table.tableName(), cursor, lastId);
            this.updateTask(bizSyncTaskService.getById(task.getId()), state);
            if (properties.isLogBatch()) {
                log.info("同步任务 [{}] 表 [{}] 已处理至 {} / {}", task.getName(), table.tableName(),
                    cursor, lastId);
            }
            if (state.pauseRequested()) {
                return;
            }
        }
    }

    private void syncBatch(BizSyncTaskDO task, SyncTaskState state,
        SyncTargetWriter.TargetConnection connection, SyncTable table, List<SyncRecord> batch) {
        List<String> ids = batch.stream().map(SyncRecord::id).toList();
        Map<String, LocalDateTime> existing =
            syncTargetWriter.selectExisting(connection, table, ids);
        ConflictPolicy policy = ConflictPolicy.parse(task.getConflictPolicy());
        List<SyncRecord> writable = new ArrayList<>();
        List<BizSyncConflictDO> conflicts = new ArrayList<>();
        long skipped = 0L;
        for (SyncRecord record : batch) {
            SyncRecord target =
                existing.containsKey(record.id()) ? SyncRecord.of(record.id(), existing
                    .get(record.id())) : null;
            ConflictDecision decision = ConflictResolver.resolve(policy, record, target);
            switch (decision.outcome()) {
                case INSERTED, UPDATED -> writable.add(record);
                case KEPT_TARGET, PENDING_MANUAL ->
                    conflicts.add(this.toConflict(task, table, record,
                        decision));
                default -> skipped++;
            }
        }
        int written = syncTargetWriter.upsert(connection, table, writable, existing.keySet());
        state.success().addAndGet(written);
        state.skipped().addAndGet(skipped);
        state.conflict().addAndGet(conflicts.size());
        if (!conflicts.isEmpty()) {
            bizSyncConflictService.saveBatch(conflicts, conflicts.size());
        }
    }

    private BizSyncConflictDO toConflict(BizSyncTaskDO task, SyncTable table, SyncRecord record,
        ConflictDecision decision) {
        BizSyncConflictDO entity = new BizSyncConflictDO();
        entity.setCreateUser(0L);
        entity.setTaskId(task.getId());
        entity.setTableName(table.tableName());
        entity.setRecordId(record.id());
        entity.setSourceTime(this.toLocalDateTime(decision.sourceTime()));
        entity.setTargetTime(this.toLocalDateTime(decision.targetTime()));
        entity.setKeptSource(decision.writable());
        entity.setPolicy(ConflictPolicy.parse(task.getConflictPolicy()).name());
        entity.setDetail(decision.reason());
        return entity;
    }

    private void updateTask(BizSyncTaskDO task, SyncTaskState state) {
        if (task == null) {
            return;
        }
        task.setTaskStatus(state.status());
        task.setTotalCount(state.total().get());
        task.setSuccessCount(state.success().get());
        task.setFailCount(state.fail().get());
        bizSyncTaskService.updateById(task);
    }

    /**
     * 计算本次运行的截止时间
     *
     * @return 截止时间（毫秒时间戳，0 表示由配置关闭了时长上限）
     */
    private long deadlineOf() {
        Duration timeout = properties.getRunTimeout();
        long millis = timeout == null ? 0L : timeout.toMillis();
        return millis <= 0 ? 0L : System.currentTimeMillis() + millis;
    }

    /**
     * 检查运行时长，超限即抛出（由 {@link #run} 的统一异常出口置为 FAILED）
     *
     * @param state 任务运行状态
     */
    private void checkTimeout(SyncTaskState state) {
        if (state.timedOut()) {
            throw new IllegalStateException("同步运行超过 " + this.describe(properties.getRunTimeout())
                + " 上限，已中止（已同步部分已保存水位，可续传）");
        }
    }

    /**
     * 把时长上限渲染成运维看得懂的话（Duration.toString 是 ISO-8601 的 PT2S，现场读不懂）
     *
     * @param timeout 时长
     * @return 文本，如「120 分钟」「2 秒」
     */
    private String describe(Duration timeout) {
        long seconds = timeout == null ? 0L : timeout.toSeconds();
        if (seconds >= 3600 && seconds % 3600 == 0) {
            return (seconds / 3600) + " 小时";
        }
        if (seconds >= 60 && seconds % 60 == 0) {
            return (seconds / 60) + " 分钟";
        }
        return seconds + " 秒";
    }

    private void markPaused(BizSyncTaskDO task, SyncTaskState state) {
        state.status(STATUS_PAUSED);
        state.finishedAt(System.currentTimeMillis());
        BizSyncTaskDO latest = bizSyncTaskService.getById(task.getId());
        if (latest != null) {
            latest.setTaskStatus(STATUS_PAUSED);
            latest.setTotalCount(state.total().get());
            latest.setSuccessCount(state.success().get());
            latest.setFailCount(state.fail().get());
            bizSyncTaskService.updateById(latest);
        }
        log.info("同步任务 [{}] 已暂停，可从水位续传", task.getName());
    }

    private BizSyncWatermarkDO watermark(Long targetId, String tableName) {
        return bizSyncWatermarkService.lambdaQuery()
            .eq(BizSyncWatermarkDO::getTargetId, targetId)
            .eq(BizSyncWatermarkDO::getTableName, tableName)
            .one();
    }

    private void saveWatermark(Long targetId, String tableName, LocalDateTime time, String id) {
        if (this.watermark(targetId, tableName) == null) {
            BizSyncWatermarkDO entity = new BizSyncWatermarkDO();
            entity.setCreateUser(0L);
            entity.setTargetId(targetId);
            entity.setTableName(tableName);
            entity.setLastRecordTime(time);
            entity.setLastId(id);
            try {
                bizSyncWatermarkService.save(entity);
                return;
            } catch (DuplicateKeyException e) {
                // 并发或两个任务共用同一 target+table 时，两方都查到 null 会同时 insert，
                // 唯一键 uk_target_table_deleted 挡住后按更新处理，避免整个任务因此 FAILED。
                log.debug("水位已被其它运行写入，改为更新：{} / {}", targetId, tableName);
            }
        }
        // 单条 UPDATE 原子完成（不再 read-modify-write），并且不允许把水位往回退：
        // 全量任务从最早时间开始时曾把水位拉低，让增量任务重跑旧区间。
        bizSyncWatermarkService.lambdaUpdate()
            .eq(BizSyncWatermarkDO::getTargetId, targetId)
            .eq(BizSyncWatermarkDO::getTableName, tableName)
            .and(wrapper -> wrapper.isNull(BizSyncWatermarkDO::getLastRecordTime)
                .or().le(BizSyncWatermarkDO::getLastRecordTime, time))
            .set(BizSyncWatermarkDO::getLastRecordTime, time)
            .set(BizSyncWatermarkDO::getLastId, id)
            .update();
    }

    private SyncTargetWriter.TargetConnection connection(BizSyncTaskDO task) {
        BizReportTargetDO target = bizReportTargetMapper.selectById(task.getTargetId());
        if (target == null || !Boolean.TRUE.equals(target.getEnabled())) {
            throw new IllegalStateException("同步目标库不存在或已停用");
        }
        if (!"DB".equalsIgnoreCase(target.getTargetType())) {
            throw new IllegalStateException("同步目标必须是数据库类型（DB）");
        }
        JSONObject config = JSONUtil.parseObj(target.getConfigJson());
        String url = config.getStr("url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("同步目标库未配置 url");
        }
        return new SyncTargetWriter.TargetConnection(SqlDialect.parse(target.getDbType()), url,
            config
                .getStr("username"),
            config.getStr("password"));
    }

    private List<SyncTable> tables(BizSyncTaskDO task) {
        List<SyncTable> tables = new ArrayList<>();
        String names = task.getTableNames();
        if (names == null || names.isBlank()) {
            // 未指定时默认同步历史数据表与报警表
            tables.add(SyncTable.TBHISTORY);
            tables.add(SyncTable.TBALARM);
            return tables;
        }
        for (String name : names.split(",")) {
            SyncTable table = SyncTable.parse(name);
            if (table != null && !tables.contains(table)) {
                tables.add(table);
            }
        }
        return tables;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        return null;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 990 ? message.substring(0, 990) : message;
    }
}
