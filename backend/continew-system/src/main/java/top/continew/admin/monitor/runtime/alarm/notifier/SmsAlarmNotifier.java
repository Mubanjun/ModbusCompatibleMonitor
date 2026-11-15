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
import lombok.RequiredArgsConstructor;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.runtime.alarm.AlarmNotifier;
import top.continew.admin.system.model.entity.SmsConfigDO;
import top.continew.admin.system.service.SmsConfigService;

/**
 * 短信通知通道（FR-ALM-04），复用基座已配置的短信服务商。
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Component
@RequiredArgsConstructor
public class SmsAlarmNotifier implements AlarmNotifier {

    private final SmsConfigService smsConfigService;

    @Override
    public String notifyType() {
        return "SMS";
    }

    @Override
    public void send(String target, String content) {
        if (StrUtil.isBlank(target)) {
            throw new IllegalStateException("未配置短信通知目标（ALARM_NOTIFY_SMS_TARGET）");
        }
        SmsConfigDO config = smsConfigService.getDefaultConfig();
        if (config == null) {
            throw new IllegalStateException("未配置可用的短信服务商");
        }
        SmsBlend blend = SmsFactory.getSmsBlend(String.valueOf(config.getId()));
        if (blend == null) {
            throw new IllegalStateException("短信服务商 [" + config.getId() + "] 未加载");
        }
        blend.sendMessage(target, content);
    }
}
