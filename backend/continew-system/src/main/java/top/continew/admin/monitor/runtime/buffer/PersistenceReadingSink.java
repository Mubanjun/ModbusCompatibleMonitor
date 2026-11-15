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

import com.mbj.jdcompatiblemonitor.device.runtime.AcquisitionSample;
import com.mbj.jdcompatiblemonitor.device.runtime.DeviceStatus;
import com.mbj.jdcompatiblemonitor.device.runtime.ReadingSink;
import com.mbj.jdcompatiblemonitor.storage.StagingRecord;
import com.mbj.jdcompatiblemonitor.storage.StagingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 采集结果落地入口：先把样本写入本地暂存（FR-BUF-01/05），再由 {@link StagingBufferService} 批量上传。
 *
 * <p>采集线程只做入队，SQLite 写入与业务库上传都在独立线程完成，
 * 因此业务库不可用或磁盘短暂变慢都不会阻塞采集（NFR-FAULT-05 降级策略）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistenceReadingSink implements ReadingSink {

    private final StagingProperties properties;
    private final StagingStore stagingStore;

    /** 单个落地周期最多搬运的批次数（追赶上限，避免积压越滚越大） */
    private static final int DRAIN_BATCHES_PER_CYCLE = 8;

    private final ConcurrentLinkedQueue<StagingRecord> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentMap<Long, String> deviceStates = new ConcurrentHashMap<>();
    private final AtomicLong enqueued = new AtomicLong(0L);
    private final AtomicLong dropped = new AtomicLong(0L);

    @Override
    public void accept(AcquisitionSample sample) {
        if (queue.size() >= properties.getMaxInMemoryRecords()) {
            // 背压保护：暂存写入跟不上时丢弃最旧的内存样本，保证内存有界
            queue.poll();
            dropped.incrementAndGet();
        }
        queue.offer(this.toRecord(sample));
        enqueued.incrementAndGet();
    }

    @Override
    public void onDeviceState(long portId, long deviceId, DeviceStatus status) {
        deviceStates.put(deviceId, status.name());
    }

    /**
     * 把内存中的样本批量写入暂存库
     *
     * @return 写入条数
     */
    public int drainToStore() {
        if (queue.isEmpty()) {
            return 0;
        }
        List<StagingRecord> batch = new ArrayList<>();
        StagingRecord record;
        int batchSize = Math.max(properties.getBatchSize(), 1);
        // 单周期最多搬 batchSize * DRAIN_BATCHES_PER_CYCLE 条。原来每周期只搬一批（默认 500 条），
        // 采集侧持续高于这个速率时内存队列必然写满、开始丢最旧样本（只累加 dropped，页面看不出来）。
        int maxRecords = batchSize * DRAIN_BATCHES_PER_CYCLE;
        int written = 0;
        while (written < maxRecords) {
            batch = new ArrayList<>();
            while (batch.size() < batchSize && (record = queue.poll()) != null) {
                batch.add(record);
            }
            if (batch.isEmpty()) {
                break;
            }
            try {
                written += stagingStore.append(batch);
            } catch (Exception e) {
                // 批次已经出队，写失败必须退回队列，否则这批数据永久丢失且不计入 dropped；
                // 退回避开了丢数据，队列上限仍由 accept() 的背压保护兜底。
                queue.addAll(batch);
                throw new IllegalStateException("暂存写入失败，本批 " + batch.size()
                    + " 条已退回内存队列等待下次重试：" + e.getMessage(), e);
            }
        }
        return written;
    }

    /**
     * 取走并清空设备状态变化
     *
     * @return 设备 ID 到状态名的映射
     */
    public Map<Long, String> drainStates() {
        if (deviceStates.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> states = new HashMap<>(deviceStates);
        deviceStates.clear();
        return states;
    }

    /** 内存中待写入暂存的样本条数。 */
    public long queuedCount() {
        return queue.size();
    }

    /** 因背压被丢弃的样本条数。 */
    public long droppedCount() {
        return dropped.get();
    }

    /** 累计入队条数。 */
    public long enqueuedCount() {
        return enqueued.get();
    }

    /**
     * 转换成暂存记录
     *
     * @param sample 采集样本
     * @return 暂存记录
     */
    private StagingRecord toRecord(AcquisitionSample sample) {
        String quality =
            sample.overRange() ? StagingRecord.QUALITY_BAD : StagingRecord.QUALITY_GOOD;
        return new StagingRecord(0L, sample.portId(), sample.deviceId(), sample.deviceName(),
            sample.deviceAddr(), sample.channelNo(), sample.analogNo(),
            sample.elementType() == null ? null : sample.elementType().name(),
            sample.elementName(), sample.unit(), sample.rawValue(), sample.engineeringValue(),
            quality, sample.overRange(), sample.collectTime());
    }
}
