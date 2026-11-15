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

package top.continew.admin.monitor.runtime.buffer;

import com.mbj.jdcompatiblemonitor.storage.SqliteStagingStore;
import com.mbj.jdcompatiblemonitor.storage.StagingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地暂存库装配（FR-BUF-01）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Configuration
public class StagingConfiguration {

    /**
     * 构建并打开本地暂存库
     *
     * @param properties 暂存参数
     * @return 暂存库
     */
    @Bean(destroyMethod = "close")
    public StagingStore stagingStore(StagingProperties properties) {
        SqliteStagingStore store = new SqliteStagingStore(properties.getDatabasePath());
        store.open();
        log.info("本地暂存库已就绪：{}（WAL 模式）", properties.getDatabasePath());
        return store;
    }
}
