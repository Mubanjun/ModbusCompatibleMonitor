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

package top.continew.admin.monitor.runtime.alarm.notifier;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.runtime.alarm.AlarmNotifier;
import top.continew.starter.messaging.mail.util.MailUtils;

/**
 * 邮件通知通道（FR-ALM-04），使用基座的邮件能力。
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Component
public class MailAlarmNotifier implements AlarmNotifier {

    /** 邮件主题固定为报警通知 */
    private static final String SUBJECT = "仁科兼容环境监测平台 · 报警通知";

    @Override
    public String notifyType() {
        return "EMAIL";
    }

    @Override
    public void send(String target, String content) throws Exception {
        if (StrUtil.isBlank(target)) {
            throw new IllegalStateException("未配置邮件通知目标（ALARM_NOTIFY_EMAIL_TARGET）");
        }
        for (String address : StrUtil.splitTrim(target, ',')) {
            MailUtils.sendText(address, SUBJECT, content);
        }
    }
}
