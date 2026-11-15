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

package top.continew.admin.monitor.runtime.cluster;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多实例与分布式锁参数（NFR-SCALE-04）
 *
 * <p>默认关闭：单机部署时行为与以前完全一致（本实例即主节点，各引擎照常启动）。
 * 打开后多个实例通过 Redis 抢同一把租约锁，只有主节点承担采集/上报/暂存，
 * 其余实例只提供查询与配置，从而支持「采集与业务拆分 / 多实例」。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.cluster")
public class ClusterProperties {

    /**
     * 是否启用多实例模式（Redis 选主）
     */
    private boolean enabled = false;

    /**
     * 主节点租约键
     */
    private String lockKey = "jdrk:cluster:leader";

    /**
     * 租约时长（秒）：主节点崩溃后，其他实例最多等这么久接管
     */
    private int leaseSeconds = 30;

    /**
     * 续租间隔（秒），必须小于租约时长
     */
    private int renewSeconds = 10;

    /**
     * 实例标识，留空时自动生成「主机名-pid」
     */
    private String instanceId;
}
