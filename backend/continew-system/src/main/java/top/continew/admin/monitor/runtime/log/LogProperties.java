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

package top.continew.admin.monitor.runtime.log;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 日志与诊断运行参数（FR-LOG-01/03/04）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.log")
public class LogProperties {

    /**
     * 日志根目录（与 logback LOG_PATH 保持一致）
     */
    private String path = "./logs";

    /**
     * 单条日志文件允许打进诊断包的大小上限（MB）
     */
    private int maxEntryMb = 50;

    /**
     * 诊断包内日志总量上限（MB）
     */
    private int maxLogTotalMb = 200;

    /**
     * 诊断包输出子目录
     */
    private String diagnosticDir = "diagnostic";
}
