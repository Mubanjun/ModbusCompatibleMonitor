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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地暂存与数据落地的运行参数（FR-BUF-01/02/04、FR-CFG-03）。
 *
 * <p>容量与保留天数优先取 {@code biz_sys_config} 的 STORAGE 分类，缺省时用本类默认值。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.staging")
public class StagingProperties {

    /**
     * 暂存库文件路径（相对于进程工作目录）
     */
    private String databasePath = "data/buffer/jdrk-buffer.db";

    /**
     * 落地周期（毫秒，FR-BUF-02 定时批量上传）
     */
    private int flushIntervalMs = 1000;

    /**
     * 单批上传条数
     */
    private int batchSize = 500;

    /**
     * 单个落地周期内最多上传的批次数（背压后的追赶能力，NFR-PERF-04/06）
     *
     * <p>满配矩阵（16 端口 × 32 从站 × 32 通道 × 2 模拟量）单轮就是 3 万+ 采样，
     * 每周期只上传一批（500 行/秒）必然积压；该值把追赶速率提高到 batchSize × 该值/周期。</p>
     */
    private int maxBatchesPerCycle = 20;

    /**
     * 暂存容量上限（行数，FR-BUF-04），可被 STORAGE_BUFFER_MAX_ROWS 覆盖
     */
    private long maxRows = 1_000_000L;

    /**
     * 内存中待写入暂存的样本上限（背压保护，NFR-FAULT-04）
     */
    private int maxInMemoryRecords = 200_000;

    /**
     * 滚动清理周期（毫秒）
     */
    private int purgeIntervalMs = 600_000;

    /**
     * 历史数据默认保留天数，可被 STORAGE_RETAIN_DAYS 覆盖
     */
    private int retainDays = 90;

    /**
     * 磁盘剩余空间告警阈值（MB，NFR-FAULT-03），可被 STORAGE_MIN_FREE_MB 覆盖
     */
    private long minFreeMb = 512L;

    /**
     * 磁盘剩余空间保护阈值（MB，NFR-FAULT-03）：低于该值进入保护模式，可被 STORAGE_PROTECT_FREE_MB 覆盖
     */
    private long protectFreeMb = 128L;
}
