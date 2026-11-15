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

package top.continew.admin.monitor.runtime.alarm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 报警引擎运行参数（FR-ALM-04/05）。
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.alarm")
public class AlarmProperties {

    /**
     * 规则缓存有效期（毫秒）
     */
    private int ruleCacheMillis = 10_000;

    /**
     * 通知频率控制窗口（毫秒）
     */
    private long notifyWindowMillis = 300_000L;

    /**
     * 单个规则在窗口内最多通知次数（FR-ALM-04 通知次数与频率控制）
     */
    private int notifyMaxPerWindow = 5;

    /**
     * 通知失败最大重试次数
     */
    private int notifyMaxRetry = 3;

    /**
     * 通知重试扫描间隔（毫秒）
     */
    private int notifyRetryIntervalMillis = 30_000;

    /**
     * 单次重试扫描最多处理的条数
     */
    private int notifyRetryBatchSize = 50;

    /**
     * 通知重试的退避基数（毫秒）
     */
    private long notifyRetryBackoffMillis = 60_000L;
}
