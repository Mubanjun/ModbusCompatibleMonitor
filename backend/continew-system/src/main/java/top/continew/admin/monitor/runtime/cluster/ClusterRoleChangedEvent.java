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

/**
 * 集群角色变化事件（NFR-SCALE-04）
 *
 * @param leader 是否为主节点
 * @param reason 变化原因（启动/续租失效/Redis 异常等）
 * @param instanceId 实例标识
 * @author jdrk
 * @since 2026/9/25
 */
public record ClusterRoleChangedEvent(boolean leader, String reason, String instanceId) {
}
