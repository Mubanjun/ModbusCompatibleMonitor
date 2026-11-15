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

package top.continew.admin.monitor.runtime.relay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 继电器控制运行参数（FR-RLY-02/04）。
 *
 * <p>是否允许写入优先取 {@code biz_sys_config} 的 SYSTEM 分类（RELAY_WRITE_ENABLED），
 * 缺省时使用本类默认值。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.relay")
public class RelayProperties {

    /**
     * 是否允许下单继电器写入（写保护，FR-PROTO-09）
     */
    private boolean writeEnabled = true;

    /**
     * 最大执行时长（毫秒）
     */
    private int maxDurationMs = 600_000;

    /**
     * 状态查询时读取的寄存器最大个数
     */
    private int maxRelays = 32;
}
