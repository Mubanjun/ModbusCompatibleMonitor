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

package top.continew.admin.monitor.runtime.alarm;

/**
 * 报警通知通道 SPI（FR-ALM-04）。
 *
 * <p>新增通知方式只需新增一个实现并声明 {@link #notifyType()}，调度与重试逻辑无需改动。
 * 通道只依赖「目标 + 正文」，因此失败重试可以直接从 {@code biz_alarm_notify} 记录重放。</p>
 */
public interface AlarmNotifier {

    /**
     * 通知方式名称，需与规则里的 notify_methods 取值一致
     *
     * @return 通知方式（MQTT / EMAIL / SMS）
     */
    String notifyType();

    /**
     * 发送通知，失败需抛出异常并给出可读原因
     *
     * @param target  通知目标（主题 / 邮箱 / 手机号）
     * @param content 通知正文
     * @throws Exception 发送失败
     */
    void send(String target, String content) throws Exception;
}
