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

package top.continew.admin.monitor.runtime.security;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback 消息脱敏转换器（NFR-SEC-07）
 *
 * <p>在 logback-spring.xml 里注册为转换词 maskedMsg，并把日志 pattern 里的 %msg 换成它：
 * 日志文件、按模块切分的日志以及 FR-LOG-04 诊断包里出现的手机号/身份证/邮箱/银行卡都会被脱敏，
 * 不需要逐处改业务代码。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
public class MaskedMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        if (event == null) {
            return null;
        }
        return MaskUtils.maskText(event.getFormattedMessage());
    }
}
