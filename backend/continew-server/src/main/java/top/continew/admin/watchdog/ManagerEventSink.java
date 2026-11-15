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

package top.continew.admin.watchdog;

/**
 * 管理器事件出口：把「运行日志」与「重启事件」分开。
 *
 * <p>被 {@link ProcessManager} 实现，供 {@link ManagedService} 回写；
 * 独立成顶层类型是因为「类实现自己的嵌套接口」会被 javac 判为循环继承。</p>
 *
 * @author jdrk
 * @since 2026/11/5
 */
interface ManagerEventSink {

    /**
     * 写运行日志
     *
     * @param message 文本
     */
    void log(String message);

    /**
     * 写一条重启/限流事件
     *
     * @param service      服务名
     * @param eventType    事件类型
     * @param reason       原因
     * @param pid          相关进程号
     * @param restartCount 累计重启次数
     */
    void record(String service, String eventType, String reason, Long pid, int restartCount);
}
