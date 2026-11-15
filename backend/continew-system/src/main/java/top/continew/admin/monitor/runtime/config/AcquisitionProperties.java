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

package top.continew.admin.monitor.runtime.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 采集内核的运行参数（默认值可用 {@code biz_sys_config} 的 COLLECT 分类覆盖）。
 *
 * <p>外置配置优先：{@code biz_sys_config} 中的值会覆盖本类默认值
 * （NFR-MAINT-11 不硬编码端口、地址、阈值）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.acquisition")
public class AcquisitionProperties {

    /**
     * 是否随应用启动自动开始采集
     */
    private boolean autoStart = true;

    /**
     * 调度轮询周期（毫秒）
     */
    private int tickMs = 200;

    /**
     * 信令留痕开关（FR-SIG-05：可开关，关闭时不影响采集）
     */
    private boolean traceEnabled = true;

    /**
     * 内存信令环形缓冲容量
     */
    private int traceBufferSize = 2000;

    /**
     * 信令批量落库的批次大小
     */
    private int traceBatchSize = 200;

    /**
     * 信令批量落库的间隔（毫秒）
     */
    private int traceFlushIntervalMs = 2000;

    /**
     * 信令保留时长（小时，FR-SIG-03 滚动留存）
     */
    private int traceRetainHours = 24;

    /**
     * 单设备一次轮询允许发送的最大报文数（避免异常设备刷屏）
     */
    private int maxFramesPerDevice = 200;
}
