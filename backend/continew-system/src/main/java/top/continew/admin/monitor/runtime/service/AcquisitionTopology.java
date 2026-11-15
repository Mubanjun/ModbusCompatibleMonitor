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

package top.continew.admin.monitor.runtime.service;

import top.continew.admin.monitor.runtime.session.PortSession;

import java.util.List;

/**
 * 一次配置加载的结果：端口会话及其下属设备会话。
 *
 * @param ports 端口会话列表
 * @author jdrk
 * @since 2026/9/23
 */
public record AcquisitionTopology(List<PortSession> ports) {
}
