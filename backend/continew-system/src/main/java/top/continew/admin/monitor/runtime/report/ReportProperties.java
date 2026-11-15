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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上报引擎运行参数（FR-RPT-01/05/08）。
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.report")
public class ReportProperties {

    /**
     * 调度周期（毫秒）
     */
    private int tickMs = 500;

    /**
     * 单个规则的内存缓冲上限（条），超出丢弃最旧数据（背压保护）
     */
    private int maxBufferPerRule = 100_000;

    /**
     * 单批最大上报条数
     *
     * <p>默认 2 万。实测（40 端口 × 32 从站 × 8 通道、引擎最小间隔 1s、源侧 ≈5.7k 采样/秒）：
     * 单批 5,000 时每周期上限 5,000 条 × ~0.9 周期/秒 ≈ **4.0k 条/秒**，规则缓冲持续增长；
     * 放宽到 20,000 后上报 **5,749 条/秒**（100% 跟上采集）、失败批次 0、死信 0、缓冲稳定在几百条。
     */
    private int batchMax = 20_000;

    /**
     * 规则缓存有效期（毫秒）
     */
    private int ruleCacheMillis = 10_000;

    /**
     * 死信队列容量上限（条），超出清理最旧记录
     */
    private int deadLetterMax = 10_000;

    /**
     * MQTT 连接超时（秒）
     */
    private int mqttConnectTimeoutSeconds = 10;

    /**
     * 是否打印上报报文（排障用）
     */
    private boolean logPayload = false;
}
