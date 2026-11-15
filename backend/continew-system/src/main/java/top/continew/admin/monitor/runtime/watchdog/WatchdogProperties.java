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

package top.continew.admin.monitor.runtime.watchdog;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 看门狗参数（FR-WD-01~06）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.watchdog")
public class WatchdogProperties {

    /**
     * 心跳与重启记录目录
     */
    private String dir = "./watchdog";

    /**
     * 被监控进程名
     */
    private String processName = "continew-admin";

    /**
     * 后端心跳写入间隔（秒）
     */
    private int heartbeatIntervalSeconds = 10;

    /**
     * 心跳超时阈值（秒），超过即判定失联（FR-WD-02）
     */
    private int heartbeatTimeoutSeconds = 120;

    /**
     * 单位时间内（1 小时）允许的最大重启次数（FR-WD-05）
     */
    private int restartLimitPerHour = 5;

    /**
     * 状态接口返回的历史重启记录条数上限
     */
    private int restartHistoryLimit = 50;

    /**
     * 启动后是否自动执行一次自检（FR-WD-06）
     */
    private boolean selfCheckOnStartup = true;

    /**
     * 看门狗日志文件路径（供状态接口读取尾部）
     */
    private String watchdogLog = "./logs/watchdog.log";

    /**
     * 心跳接口共享令牌（为空则不校验；配置后看门狗需带 X-Watchdog-Token 头）
     */
    private String heartbeatToken;
}
