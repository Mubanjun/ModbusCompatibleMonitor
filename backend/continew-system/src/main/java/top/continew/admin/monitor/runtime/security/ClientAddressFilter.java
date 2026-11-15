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

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 管理端口来源地址白名单过滤器（NFR-SEC-04）
 *
 * <p>在任何业务处理之前判定来源地址：命中 jdrk.security.allowed-cidrs 的请求正常放行，
 * 其余一律返回 403 并记录 WARN。默认白名单只含回环与私网，因此平台天然不会被公网访问到；
 * 现场要从严时把 allowed-cidrs 收成具体网段即可。</p>
 *
 * <p>注意：只有当 jdrk.security.trust-forwarded-for 显式打开时才会读 X-Forwarded-For，
 * 因为该请求头可被客户端伪造，默认信任等于白名单失效。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class ClientAddressFilter extends OncePerRequestFilter {

    private final SecurityProperties properties;

    /** 解析后的网段：启动时构建，配置写错立即启动失败（不静默放行） */
    private List<CidrMatcher> matchers;

    /**
     * 启动时把配置里的 CIDR 解析成匹配器
     */
    @PostConstruct
    void init() {
        this.matchers = CidrMatcher.parseAll(properties.getAllowedCidrs());
        log.info("来源地址白名单{}：{}", properties.isEnabled() ? "已启用" : "已关闭", matchers);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        String client = this.resolveClient(request);
        InetAddress address = this.parse(client);
        if (CidrMatcher.anyMatch(this.matchers, address)) {
            if (properties.isLogAllowed()) {
                log.debug("放行来源 {} 访问 {} {}", client, request.getMethod(), request.getRequestURI());
            }
            chain.doFilter(request, response);
            return;
        }
        log.warn("拒绝来源 {} 访问 {} {}：不在 jdrk.security.allowed-cidrs 白名单内", client, request
            .getMethod(), request.getRequestURI());
        this.reject(response, client);
    }

    /**
     * 返回 403 与可读原因（前端会原样展示 msg）
     *
     * @param response 响应
     * @param client 来源地址
     * @throws IOException 写响应失败
     */
    private void reject(HttpServletResponse response, String client) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"msg\":\"来源地址 " + client
            + " 不在允许范围（jdrk.security.allowed-cidrs）内\",\"data\":null}");
    }

    /**
     * 解析来源地址（默认只信 TCP 对端地址）
     *
     * @param request 请求
     * @return 来源地址文本
     */
    private String resolveClient(HttpServletRequest request) {
        if (properties.isTrustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf((char) 44);
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 文本地址转 InetAddress
     *
     * @param client 来源地址文本
     * @return 地址；无法解析时返回 null（按拒绝处理）
     */
    private InetAddress parse(String client) {
        try {
            return InetAddress.getByName(client);
        } catch (UnknownHostException e) {
            log.warn("无法解析来源地址 {}：{}", client, e.getMessage());
            return null;
        }
    }
}
