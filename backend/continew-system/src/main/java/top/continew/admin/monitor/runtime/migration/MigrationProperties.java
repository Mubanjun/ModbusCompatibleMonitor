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

package top.continew.admin.monitor.runtime.migration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 迁移与无损替换参数（FR-IO-06、FR-MIG-01~06）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.migration")
public class MigrationProperties {

    /**
     * 运行模式：OFF（仅本系统）/ COEXIST（与仁科并存错峰）/ SWITCHED（已切换）
     */
    private String mode = "OFF";

    /**
     * 共存模式下暂停本系统采集的错峰窗口（HH:mm，起止同值表示不启用）
     */
    private String quietWindowStart = "00:00";

    /**
     * 错峰窗口结束（HH:mm）
     */
    private String quietWindowEnd = "00:00";

    /**
     * 迁移批量大小
     */
    private int batchSize = 500;

    /**
     * 一致性校验抽样条数
     */
    private int sampleSize = 20;

    /**
     * 源库连接超时（秒）
     */
    private int connectTimeoutSeconds = 5;

    /**
     * 迁移向导允许的最大时间范围（天）
     */
    private int maxRangeDays = 3650;
}
