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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端口来源白名单参数（NFR-SEC-04）
 *
 * <p>规格要求「IPC 与管理端口仅回环或内网白名单」。默认值只放行回环与 RFC1918/链路本地私网，
 * 现场需要从严时把列表收成具体网段（例如仅 192.168.74.0/24），需要更松时再追加。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.security")
public class SecurityProperties {

    /**
     * 是否启用来源白名单；关闭表示任何来源都可访问（仅用于本机联调，生产不应关闭）
     */
    private boolean enabled = true;

    /**
     * 允许访问管理端口的 CIDR 列表（回环 + 私网 + 链路本地）
     */
    private List<String> allowedCidrs = new ArrayList<>(List.of("127.0.0.1/32", "::1/128",
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "fe80::/10",
        "fc00::/7"));

    /**
     * 是否信任反向代理写入的 X-Forwarded-For；默认关闭（该头可伪造，开启等于允许绕过白名单）
     */
    private boolean trustForwardedFor = false;

    /**
     * 是否记录放行日志（默认关闭，避免高频刷新把日志刷满）
     */
    private boolean logAllowed = false;
}
