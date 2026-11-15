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

package top.continew.admin.monitor.runtime.io;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 备份与恢复参数（FR-IO-03/05/07）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.backup")
public class BackupProperties {

    /**
     * 备份包目录
     */
    private String dir = "./backups";

    /**
     * 保留份数（超出后按时间清理最旧的）
     */
    private int keep = 10;

    /**
     * 是否启用定时备份
     */
    private boolean autoEnabled = false;

    /**
     * 定时备份间隔（小时）
     */
    private long intervalHours = 24;

    /**
     * 是否把日志打进备份包
     */
    private boolean includeLogs = true;

    /**
     * 备份包内日志总量上限（MB）
     */
    private int maxLogMb = 50;

    /**
     * 异地备份目录（可选，创建后复制一份过去）
     */
    private String remoteDir;

    /**
     * 默认加密口令（非空则默认生成加密包，FR-IO-07）
     */
    private String encryptPassword;
}
