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

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mbj.jdcompatiblemonitor.report.ReportBatch;
import com.mbj.jdcompatiblemonitor.report.ReportFormat;
import com.mbj.jdcompatiblemonitor.report.ReportFormatters;
import com.mbj.jdcompatiblemonitor.report.ReportItem;
import com.mbj.jdcompatiblemonitor.report.ReportVariables;
import com.mbj.jdcompatiblemonitor.report.TemplateRenderer;
import com.mbj.jdcompatiblemonitor.device.runtime.AcquisitionSample;
import com.mbj.jdcompatiblemonitor.device.runtime.ReadingSink;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.model.entity.BizReportDeadLetterDO;
import top.continew.admin.monitor.runtime.alarm.AlarmStateRegistry;
import top.continew.admin.monitor.runtime.cluster.ClusterLeaderService;
import top.continew.admin.monitor.runtime.cluster.ClusterProperties;
import top.continew.admin.monitor.service.BizReportDeadLetterService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 上报引擎（FR-RPT-01~05/08）。
 *
 * <p>作为 {@code ReadingSink} 按规则缓冲采集数据，达到规则间隔后组批上报；
 * 失败批次进入「待重试队列」按指数退避重试，超过配置次数后落入死信队列供人工重放。</p>
 *
 * <p>多个目标互不影响：每个规则独立缓冲、独立调度、独立重试（FR-RPT-04）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportEngine implements ReadingSink {

    /** 单规则待重试批次的队列上限 */
    private static final int MAX_PENDING_PER_RULE = 5;
    /** 退避上限（毫秒） */
    private static final long MAX_BACKOFF_MILLIS = 600_000L;

    private final ReportCatalog reportCatalog;
    private final ReportProperties properties;
    private final ObjectProvider<ReportTransport> transportProvider;
    private final BizReportDeadLetterService bizReportDeadLetterService;
    private final AlarmStateRegistry alarmStateRegistry;
    private final ClusterProperties clusterProperties;
    private final ClusterLeaderService clusterLeaderService;

    private final Map<String, ReportTransport> transports = new ConcurrentHashMap<>();
    private final Map<Long, RuleBuffer> buffers = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Pending>> pendings = new ConcurrentHashMap<>();
    private final Map<Long, Long> nextSendTime = new ConcurrentHashMap<>();
    private final Map<Long, TargetStat> stats = new ConcurrentHashMap<>();

    private final AtomicLong sentBatches = new AtomicLong(0L);
    private final AtomicLong sentItems = new AtomicLong(0L);
    private final AtomicLong failedBatches = new AtomicLong(0L);
    private final AtomicLong retriedBatches = new AtomicLong(0L);
    private final AtomicLong deadLetters = new AtomicLong(0L);
    private final AtomicLong droppedItems = new AtomicLong(0L);

    private volatile int loadedVersion = -1;
    private volatile ScheduledExecutorService ticker;

    /**
     * 应用就绪后加载通道并启动调度
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (clusterProperties.isEnabled() && !clusterLeaderService.isLeader()) {
            log.info("集群模式：本实例为从节点，上报调度不启动（由主节点上报）");
            return;
        }
        if (this.ticker != null) {
            // 集群角色升主时会再次调用 start()，这里保证不会起第二个调度线程
            return;
        }
        for (ReportTransport transport : transportProvider.orderedStream().toList()) {
            transports.put(transport.type().toUpperCase(), transport);
        }
        log.info("上报通道：{}", transports.keySet());
        long tick = Math.max(properties.getTickMs(), 200);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "acq-report-ticker");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, tick, tick, TimeUnit.MILLISECONDS);
        this.ticker = executor;
    }

    /**
     * 停止调度并释放通道
     */
    @PreDestroy
    public void stop() {
        ScheduledExecutorService executor = ticker;
        ticker = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        for (ReportTransport transport : transports.values()) {
            try {
                transport.close();
            } catch (Exception e) {
                log.debug("关闭上报通道失败：{}", e.getMessage());
            }
        }
    }

    @Override
    public void accept(AcquisitionSample sample) {
        List<ReportRuleSpec> rules = reportCatalog.rules();
        if (rules.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ReportRuleSpec rule : rules) {
            if (!rule.matches(sample.portId(), sample.deviceId())) {
                continue;
            }
            buffers.computeIfAbsent(rule.id(), key -> new RuleBuffer()).add(
                this.toItem(sample, now),
                sample.portName(), sample.deviceId(), properties.getMaxBufferPerRule(),
                droppedItems);
        }
    }

    /**
     * 上报引擎运行状态（FR-RPT-08）
     *
     * @return 状态
     */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("channels", new ArrayList<>(transports.keySet()));
        status.put("rules", reportCatalog.rules().size());
        status.put("sentBatches", sentBatches.get());
        status.put("sentItems", sentItems.get());
        status.put("failedBatches", failedBatches.get());
        status.put("retriedBatches", retriedBatches.get());
        status.put("deadLetters", deadLetters.get());
        status.put("droppedItems", droppedItems.get());
        long buffered = 0L;
        for (RuleBuffer buffer : buffers.values()) {
            buffered += buffer.size();
        }
        status.put("bufferedItems", buffered);
        List<Map<String, Object>> targets = new ArrayList<>();
        for (TargetStat stat : stats.values()) {
            targets.add(stat.toMap());
        }
        status.put("targets", targets);
        return status;
    }

    /**
     * 调度一次：先重试到点批次，再按间隔组批上报
     */
    private void tick() {
        try {
            List<ReportRuleSpec> rules = reportCatalog.rules();
            Set<Long> alive = new HashSet<>();
            for (ReportRuleSpec rule : rules) {
                alive.add(rule.id());
            }
            buffers.keySet().removeIf(id -> !alive.contains(id));
            pendings.keySet().removeIf(id -> !alive.contains(id));
            if (reportCatalog.version() != loadedVersion) {
                loadedVersion = reportCatalog.version();
            }
            long now = System.currentTimeMillis();
            for (ReportRuleSpec rule : rules) {
                this.retryPending(rule, now);
                Long due = nextSendTime.get(rule.id());
                if (due == null) {
                    nextSendTime.put(rule.id(), now + rule.intervalMs());
                    continue;
                }
                if (now < due) {
                    continue;
                }
                nextSendTime.put(rule.id(), now + rule.intervalMs());
                this.flush(rule);
            }
        } catch (Exception e) {
            log.error("上报调度异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 组批并上报一个规则
     *
     * @param rule 规则
     */
    private void flush(ReportRuleSpec rule) {
        RuleBuffer buffer = buffers.get(rule.id());
        if (buffer == null || buffer.isEmpty()) {
            return;
        }
        List<ReportItem> items = buffer.drain(properties.getBatchMax());
        if (items.isEmpty()) {
            return;
        }
        ReportBatch batch =
            new ReportBatch(rule.id(), rule.name(), rule.target().id(), rule.target()
                .name(), buffer.lastPortName(), System.currentTimeMillis(), items);
        this.send(rule, batch);
    }

    /**
     * 发送一个批次，失败进入待重试队列
     *
     * @param rule  规则
     * @param batch 批次
     */
    private void send(ReportRuleSpec rule, ReportBatch batch) {
        try {
            this.transmit(rule, batch);
            sentBatches.incrementAndGet();
            sentItems.addAndGet(batch.count());
            stats.computeIfAbsent(rule.target().id(), key -> new TargetStat(rule.target().name()))
                .success(batch.count());
            if (properties.isLogPayload()) {
                log.info("上报成功：规则 [{}] 目标 [{}] {} 条", rule.name(), rule.target().name(), batch
                    .count());
            }
        } catch (Exception e) {
            failedBatches.incrementAndGet();
            stats.computeIfAbsent(rule.target().id(), key -> new TargetStat(rule.target().name()))
                .failure(e.getMessage());
            this.enqueuePending(rule, batch, e.getMessage());
        }
    }

    /**
     * 实际传输（MQTT 按条发布，其余整批发送）
     *
     * @param rule  规则
     * @param batch 批次
     * @throws Exception 传输失败
     */
    private void transmit(ReportRuleSpec rule, ReportBatch batch) throws Exception {
        ReportTargetSpec target = rule.target();
        ReportTransport transport = transports.get(target.targetType() == null ? "" : target
            .targetType()
            .toUpperCase());
        if (transport == null) {
            throw new IllegalStateException("未接入的上报目标类型：" + target.targetType());
        }
        if (target.mqtt()) {
            for (ReportItem item : batch.items()) {
                ReportBatch single =
                    new ReportBatch(batch.ruleId(), batch.ruleName(), batch.targetId(),
                        batch.targetName(), batch.portName(), batch.batchTime(), List.of(item));
                String payload = ReportFormatters.of(target.format()).format(single, target
                    .transform(), target.templateContent());
                transport.send(new ReportTransmission(target, this.topic(target, single), single,
                    payload));
            }
            return;
        }
        String payload = ReportFormatters.of(target.format()).format(batch, target.transform(),
            target.templateContent());
        transport.send(new ReportTransmission(target, null, batch, payload));
    }

    /**
     * 把失败批次放入待重试队列
     *
     * @param rule  规则
     * @param batch 批次
     * @param error 失败原因
     */
    private void enqueuePending(ReportRuleSpec rule, ReportBatch batch, String error) {
        Deque<Pending> queue = pendings.computeIfAbsent(rule.id(), key -> new ArrayDeque<>());
        synchronized (queue) {
            if (queue.size() >= MAX_PENDING_PER_RULE) {
                Pending oldest = queue.pollFirst();
                if (oldest != null) {
                    this.toDeadLetter(rule, oldest.batch, oldest.lastError == null ? error
                        : oldest.lastError, oldest.attempt);
                }
            }
            queue.addLast(new Pending(batch, 1, System.currentTimeMillis() + this.backoff(rule, 1),
                error));
        }
    }

    /**
     * 重试到点的失败批次
     *
     * @param rule 规则
     * @param now  当前时间
     */
    private void retryPending(ReportRuleSpec rule, long now) {
        Deque<Pending> queue = pendings.get(rule.id());
        if (queue == null) {
            return;
        }
        synchronized (queue) {
            while (!queue.isEmpty()) {
                Pending head = queue.peekFirst();
                if (now < head.nextAttempt) {
                    return;
                }
                try {
                    this.transmit(rule, head.batch);
                    queue.pollFirst();
                    retriedBatches.incrementAndGet();
                    sentBatches.incrementAndGet();
                    sentItems.addAndGet(head.batch.count());
                    stats.computeIfAbsent(rule.target().id(), key -> new TargetStat(rule.target()
                        .name())).success(head.batch.count());
                } catch (Exception e) {
                    if (head.attempt >= Math.max(rule.target().retryCount(), 0)) {
                        queue.pollFirst();
                        this.toDeadLetter(rule, head.batch, e.getMessage(), head.attempt);
                    } else {
                        head.attempt++;
                        head.lastError = e.getMessage();
                        head.nextAttempt = now + this.backoff(rule, head.attempt);
                        stats
                            .computeIfAbsent(rule.target().id(), key -> new TargetStat(rule.target()
                                .name()))
                            .failure(e.getMessage());
                        return;
                    }
                }
            }
        }
    }

    /**
     * 写入死信队列
     *
     * @param rule    规则
     * @param batch   批次
     * @param error   失败原因
     * @param retries 已重试次数
     */
    private void toDeadLetter(ReportRuleSpec rule, ReportBatch batch, String error, int retries) {
        try {
            ReportTargetSpec target = rule.target();
            BizReportDeadLetterDO entity = new BizReportDeadLetterDO();
            entity.setCreateUser(0L);
            entity.setTargetId(target.id());
            entity.setRuleId(rule.id());
            entity.setTargetName(target.name());
            entity.setRuleName(rule.name());
            entity.setTargetType(target.targetType());
            entity.setFormatType(target.format().name());
            entity.setPayload(this.deadLetterPayload(target, batch));
            entity.setItemCount(batch.count());
            entity.setRetryCount(retries);
            entity.setState("DEAD");
            entity.setErrorMsg(error == null ? null : (error.length() > 990 ? error
                .substring(0, 990) : error));
            // 首次失败时间取「批次生成时间」而不是写死信的当前时间：
            // 经过重试才进死信的批次，两者可能相差几分钟，写当前时间会让页面上的「首次失败时间」失真
            entity.setFirstFailTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(batch.batchTime()),
                ZoneId.systemDefault()));
            bizReportDeadLetterService.save(entity);
            deadLetters.incrementAndGet();
            this.purgeDeadLettersIfNeeded();
            log.warn("上报进入死信：规则 [{}] 目标 [{}] {} 条，原因：{}", rule.name(), target.name(), batch
                .count(), error);
        } catch (Exception e) {
            log.error("写死信失败：{}", e.getMessage());
        }
    }

    /**
     * 生成死信报文：数据库目标固定存 JSON，便于重放时还原批次
     *
     * @param target 目标
     * @param batch  批次
     * @return 报文
     */
    private String deadLetterPayload(ReportTargetSpec target, ReportBatch batch) {
        if (target.database()) {
            // 数据库目标的死信报文只用于「重放时还原批次」，而重放解析固定按
            // deviceAddr/nodeId/analogNo/collectTimestamp 等**原始字段名**取值；
            // 这里若套上 transform（mapping/include/exclude 会改名或删字段），重放会全部落到默认值
            // （deviceAddr=0、记录时间变 1970），写出空行且不报错。因此固定存未变换的原始 JSON。
            // 数据库目标写库本身按 DR-03 固定列模型取值，不读 transform，故不影响上报内容。
            return ReportFormatters.of(ReportFormat.JSON).format(batch, null, null);
        }
        return ReportFormatters.of(target.format()).format(batch, target.transform(), target
            .templateContent());
    }

    /**
     * 渲染 MQTT 主题
     *
     * @param target 目标
     * @param batch  批次
     * @return 主题
     */
    private String topic(ReportTargetSpec target, ReportBatch batch) {
        JSONObject config = JSONUtil.parseObj(target.configJson());
        String template = config.getStr("topic", "jdrk/data");
        Map<String, Object> variables = new HashMap<>(ReportVariables.ofBatch(batch));
        if (!batch.items().isEmpty()) {
            variables.putAll(ReportVariables.ofItem(batch.items().get(0)));
        }
        return TemplateRenderer.render(template, variables);
    }

    /**
     * 取退避时长
     *
     * @param rule    规则
     * @param attempt 第几次重试（从 1 开始）
     * @return 退避毫秒数
     */
    private long backoff(ReportRuleSpec rule, int attempt) {
        long base = Math.max(rule.target().retryIntervalMs(), 100L);
        long backoff = base * (1L << Math.min(Math.max(attempt - 1, 0), 16));
        return Math.min(backoff, MAX_BACKOFF_MILLIS);
    }

    /**
     * 超容量时清理最旧死信
     */
    private void purgeDeadLettersIfNeeded() {
        if (deadLetters.get() % 50 != 1) {
            return;
        }
        try {
            bizReportDeadLetterService.purgeOverflow(Math.max(properties.getDeadLetterMax(), 100));
        } catch (Exception e) {
            log.debug("清理死信失败：{}", e.getMessage());
        }
    }

    /**
     * 采集样本转换为上报数据
     *
     * @param sample 采集样本
     * @param now    当前时间
     * @return 上报数据
     */
    private ReportItem toItem(AcquisitionSample sample, long now) {
        ReportCatalog.DeviceCoordinate coordinate = reportCatalog.coordinate(sample.deviceId());
        boolean alarm = alarmStateRegistry.alarmedAt(sample.deviceId(), sample.channelNo(), sample
            .analogNo(), sample.collectTime());
        return new ReportItem(sample.deviceAddr(), sample.deviceName(), sample.channelNo(), sample
            .analogNo(), sample.elementType() == null ? null : sample.elementType().name(),
            sample
                .elementName(),
            sample.unit(), sample.engineeringValue(), sample.rawValue(), sample
                .collectTime(),
            alarm, coordinate.lng(), coordinate.lat());
    }

    /** 单规则缓冲。 */
    private static final class RuleBuffer {

        private final Deque<ReportItem> items = new ArrayDeque<>();
        private volatile String lastPortName;

        private synchronized void add(ReportItem item, String portName, long deviceId, int maxSize,
            AtomicLong dropped) {
            if (maxSize > 0 && items.size() >= maxSize) {
                items.pollFirst();
                dropped.incrementAndGet();
            }
            items.addLast(item);
            if (portName != null) {
                lastPortName = portName;
            }
        }

        private synchronized List<ReportItem> drain(int max) {
            List<ReportItem> batch = new ArrayList<>();
            while (!items.isEmpty() && (max <= 0 || batch.size() < max)) {
                batch.add(items.pollFirst());
            }
            return batch;
        }

        private synchronized boolean isEmpty() {
            return items.isEmpty();
        }

        private synchronized int size() {
            return items.size();
        }

        private String lastPortName() {
            return lastPortName;
        }
    }

    /** 待重试批次。 */
    private static final class Pending {

        private final ReportBatch batch;
        private int attempt;
        private long nextAttempt;
        private String lastError;

        private Pending(ReportBatch batch, int attempt, long nextAttempt, String lastError) {
            this.batch = batch;
            this.attempt = attempt;
            this.nextAttempt = nextAttempt;
            this.lastError = lastError;
        }
    }

    /** 单目标统计（FR-RPT-08）。 */
    private static final class TargetStat {

        private final String name;
        private final AtomicLong batches = new AtomicLong(0L);
        private final AtomicLong items = new AtomicLong(0L);
        private final AtomicLong failures = new AtomicLong(0L);
        private volatile long lastSuccessTime;
        private volatile long lastErrorTime;
        private volatile String lastError;

        private TargetStat(String name) {
            this.name = name;
        }

        private void success(int count) {
            batches.incrementAndGet();
            items.addAndGet(count);
            lastSuccessTime = System.currentTimeMillis();
            lastError = null;
        }

        private void failure(String error) {
            failures.incrementAndGet();
            lastErrorTime = System.currentTimeMillis();
            lastError = error;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("batches", batches.get());
            map.put("items", items.get());
            map.put("failures", failures.get());
            map.put("lastSuccessTime", lastSuccessTime == 0L ? null : lastSuccessTime);
            map.put("lastErrorTime", lastErrorTime == 0L ? null : lastErrorTime);
            map.put("lastError", lastError);
            return map;
        }
    }
}
