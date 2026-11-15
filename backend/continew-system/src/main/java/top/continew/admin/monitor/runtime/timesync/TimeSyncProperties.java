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

package top.continew.admin.monitor.runtime.timesync;

import com.mbj.jdcompatiblemonitor.protocol.ModbusTimeCodec;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 设备校时运行参数（NFR-REL-06、FR-PROTO-09）。
 *
 * <p>是否允许写入、偏差容差、是否自动校时优先取 {@code biz_sys_config} 的 SYSTEM 分类
 * （{@code TIME_SYNC_WRITE_ENABLED}、{@code TIME_SYNC_TOLERANCE_SECONDS}、
 * {@code TIME_SYNC_AUTO_ENABLED}、{@code TIME_SYNC_REG_BASE}），缺省时使用本类默认值。</p>
 *
 * @author jdrk
 * @since 2026/10/1
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.time-sync")
public class TimeSyncProperties {

    /**
     * 是否允许向设备下发时间（写保护，FR-PROTO-09）
     */
    private boolean writeEnabled = true;

    /**
     * 时间寄存器起始地址（附录 A 标准规约为 684）
     */
    private int registerBase = ModbusTimeCodec.REGISTER_BASE;

    /**
     * 偏差容差（秒）：偏差不超过该值视为设备时间准确，自动校时可跳过写入
     */
    private int toleranceSeconds = 5;

    /**
     * 是否启用自动定时校时（缺省关闭，需显式开启，FR-PROTO-09）
     */
    private boolean autoEnabled = false;

    /**
     * 自动校时计划（Spring cron 表达式，6 段）
     */
    private String autoCron = "0 0 3 * * ?";

    /**
     * 单次自动校时最多处理的设备数
     */
    private int maxDevicesPerRun = 500;
}
