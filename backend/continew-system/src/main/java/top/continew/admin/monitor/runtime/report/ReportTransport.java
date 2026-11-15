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

package top.continew.admin.monitor.runtime.report;

/**
 * 上报传输 SPI（FR-RPT-03/04、NFR-SCALE-02：新增目标类型只需新增实现）。
 *
 * <p>实现需保证：失败抛出异常并给出可读原因，由 {@link ReportEngine} 负责重试与死信。</p>
 */
public interface ReportTransport {

    /**
     * 目标类型（MQTT / DB / HTTP）
     *
     * @return 目标类型
     */
    String type();

    /**
     * 发送一次上报
     *
     * @param transmission 传输输入
     * @throws Exception 发送失败
     */
    void send(ReportTransmission transmission) throws Exception;

    /**
     * 释放资源
     */
    default void close() {
    }
}
