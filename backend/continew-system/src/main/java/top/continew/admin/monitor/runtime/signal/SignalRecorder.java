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

package top.continew.admin.monitor.runtime.signal;

import com.mbj.jdcompatiblemonitor.protocol.FrameSummary;
import com.mbj.jdcompatiblemonitor.protocol.FrameTrace;
import com.mbj.jdcompatiblemonitor.protocol.Protocols;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.model.entity.BizSignalLogDO;
import top.continew.admin.monitor.runtime.config.AcquisitionProperties;
import top.continew.admin.monitor.runtime.session.DeviceSession;
import top.continew.admin.monitor.runtime.session.PortSession;
import top.continew.admin.monitor.service.BizSignalLogService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 信令记录器：把协议层累积的收发留痕转成实时流与落库记录（FR-SIG-01/03/05）。
 *
 * <p>实时流保存在有界环形缓冲里，落库按批异步进行；数据库不可用时只丢弃最旧的
 * 待落库记录，绝不阻塞采集线程（NFR-FAULT-04 背压控制、FR-SIG-05 不影响采集）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalRecorder {

    private final AcquisitionProperties properties;
    private final BizSignalLogService bizSignalLogService;

    private final Deque<SignalEntry> stream = new ArrayDeque<>();
    private final Deque<BizSignalLogDO> pending = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong(0L);

    private final AtomicLong droppedCount = new AtomicLong(0L);

    private volatile long lastFlushTime = System.currentTimeMillis();
    private volatile long lastPurgeTime = System.currentTimeMillis();

    /**
     * 收集一次设备轮询产生的信令
     *
     * @param port       端口会话
     * @param device     设备会话
     * @param roundStart 本次轮询开始时间（毫秒时间戳）
     * @param error      本次轮询的失败原因，成功为 null
     */
    public void collect(PortSession port, DeviceSession device, long roundStart, String error) {
        List<FrameTrace> traces = port.drainTraces();
        if (!properties.isTraceEnabled()) {
            return;
        }
        boolean tcp = Protocols.PORT_TYPE_TCP.equalsIgnoreCase(port.port().getPortType());
        int durationMs = (int) Math.max(System.currentTimeMillis() - roundStart, 0L);
        List<SignalEntry> entries = new ArrayList<>(traces.size() + 1);
        for (FrameTrace trace : traces) {
            String direction = trace.note() != null && trace.frame().length == 0 ? "ERROR"
                : (trace.outbound() ? "OUT" : "IN");
            String parsed = trace.note() != null && trace.frame().length == 0
                ? trace.note()
                : FrameSummary.describe(tcp, trace.outbound(), trace.frame());
            entries.add(new SignalEntry(sequence.incrementAndGet(), trace.timeMillis(),
                port.port().getId(), port.port().getPortName(), device.deviceId(),
                device.getDevice().getName(), trace.slaveAddress() < 0 ? null
                    : trace.slaveAddress(),
                direction, FrameSummary.toHex(trace.frame()), trace.frame().length, parsed,
                durationMs, error == null, trace.note() != null && trace.frame().length == 0
                    ? trace.note()
                    : null));
        }
        if (error != null) {
            // 即使没有任何收发留痕（例如打开串口就失败），也要留下一条可诊断的记录
            entries.add(new SignalEntry(sequence.incrementAndGet(), System.currentTimeMillis(),
                port.port().getId(), port.port().getPortName(), device.deviceId(),
                device.getDevice().getName(), device.slaveAddr(), "ERROR", "", 0, error, durationMs,
                false, error));
        }
        synchronized (this) {
            for (SignalEntry entry : entries) {
                stream.addLast(entry);
                pending.addLast(this.toDO(entry));
            }
            int capacity = Math.max(properties.getTraceBufferSize(), 100);
            while (stream.size() > capacity) {
                stream.pollFirst();
            }
            // 数据库落后时丢弃最旧的待落库记录，保证内存有界
            while (pending.size() > capacity * 2L) {
                pending.pollFirst();
                droppedCount.incrementAndGet();
            }
        }
    }

    /**
     * 读取序号大于 {@code sinceSequence} 的实时信令
     *
     * @param sinceSequence 起始序号（不含）
     * @param limit         最大返回条数
     * @return 信令列表
     */
    public synchronized List<SignalEntry> recent(long sinceSequence, int limit) {
        List<SignalEntry> result = new ArrayList<>();
        for (SignalEntry entry : stream) {
            if (entry.sequence() > sinceSequence) {
                result.add(entry);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 当前最大序号（供前端建立增量拉取游标）
     *
     * @return 最大序号
     */
    public long currentSequence() {
        return sequence.get();
    }

    /**
     * 已丢弃的待落库记录条数
     *
     * @return 丢弃条数
     */
    public long droppedCount() {
        return droppedCount.get();
    }

    /**
     * 到点则批量落库
     */
    public void flushIfDue() {
        if (System.currentTimeMillis() - lastFlushTime < properties.getTraceFlushIntervalMs()) {
            return;
        }
        this.flush();
    }

    /**
     * 批量落库并清理过期信令
     */
    public void flush() {
        lastFlushTime = System.currentTimeMillis();
        List<BizSignalLogDO> batch = new ArrayList<>();
        synchronized (this) {
            int batchSize = Math.max(properties.getTraceBatchSize(), 1);
            while (!pending.isEmpty() && batch.size() < batchSize) {
                batch.add(pending.pollFirst());
            }
        }
        if (!batch.isEmpty()) {
            try {
                bizSignalLogService.saveBatch(batch, batch.size());
            } catch (Exception e) {
                log.warn("信令落库失败，丢弃 {} 条：{}", batch.size(), e.getMessage());
                droppedCount.addAndGet(batch.size());
            }
        }
        this.purgeIfDue();
    }

    /**
     * 物理清理超过保留期的信令
     */
    public void purgeIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastPurgeTime < 600_000L) {
            return;
        }
        lastPurgeTime = now;
        try {
            LocalDateTime deadline =
                LocalDateTime.now().minusHours(properties.getTraceRetainHours());
            int purged = bizSignalLogService.purgeBefore(deadline);
            if (purged > 0) {
                log.info("已清理 {} 条过期信令（保留 {} 小时）", purged, properties.getTraceRetainHours());
            }
        } catch (Exception e) {
            log.warn("清理过期信令失败：{}", e.getMessage());
        }
    }

    /**
     * 转换为落库实体
     *
     * @param entry 实时信令
     * @return 实体
     */
    private BizSignalLogDO toDO(SignalEntry entry) {
        BizSignalLogDO entity = new BizSignalLogDO();
        entity.setCreateUser(0L);
        entity.setPortId(entry.portId());
        entity.setPortName(entry.portName());
        entity.setDeviceId(entry.deviceId());
        entity.setDeviceAddr(entry.deviceAddr());
        entity.setDirection(entry.direction());
        entity.setFrame(entry.frame());
        entity.setFrameLength(entry.frameLength());
        entity.setParsed(entry.parsed());
        entity.setDurationMs(entry.durationMs());
        entity.setSuccess(entry.success());
        entity.setErrorMsg(entry.errorMsg());
        entity.setTraceTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.timeMillis()),
            ZoneId.systemDefault()));
        return entity;
    }
}
