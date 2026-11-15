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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 同步引擎运行参数（FR-SYNC-04/06）。
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.sync")
public class SyncProperties {

    /**
     * 单批同步条数
     */
    private int batchSize = 500;

    /**
     * 并发执行的任务数上限
     */
    private int maxConcurrentTasks = 2;

    /**
     * 全量同步的起始时间（早于该时间的数据不同步）
     */
    private String earliestTime = "2000-01-01 00:00:00";

    /**
     * 是否打印每批同步日志
     */
    private boolean logBatch = false;

    /**
     * 单次运行的时长上限，超过即判 FAILED
     *
     * <p>没有这道闸时，目标 id 写错/目标库不可用等情况会让任务永久停在 RUNNING：
     * 页面一直转圈、无法再次启动（互斥认为它还在跑），只能重启后端。配成 0 或负数表示不限，
     * 但生产不建议——现场排障时「卡住」比「失败」难查得多。写成 Duration 而不是分钟数，
     * 是为了能用 {@code jdrk.sync.run-timeout=90s} 这样的小值做超时演练。</p>
     */
    private Duration runTimeout = Duration.ofMinutes(120);
}
