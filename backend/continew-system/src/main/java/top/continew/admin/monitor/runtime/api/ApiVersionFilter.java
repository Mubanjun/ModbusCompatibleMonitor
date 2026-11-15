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

package top.continew.admin.monitor.runtime.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 对外接口版本别名过滤器（NFR-MAINT-07）
 *
 * <p>对外集成统一使用 /monitor/v1/**：请求进来后把版本段去掉，交给现有控制器处理，
 * 响应头回写 X-Api-Version；将来要出 v2 时可以并存，老集成方不必跟着改路径。
 * 不认识的版本直接 404 并说明当前支持的版本，避免「404 但不知道哪里错」。</p>
 *
 * <p>版本路径与不带版本的路径走的是同一批控制器方法，因此鉴权、权限注解、审计
 * 这些横切行为完全一致；区别只在 URL 与响应头。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiVersionFilter extends OncePerRequestFilter {

    /** 版本前缀（/monitor/v1/...） */
    private static final String PREFIX = "/monitor/v";

    /** 当前对外版本号 */
    public static final String CURRENT_VERSION = "1";

    /** 响应头名称 */
    public static final String VERSION_HEADER = "X-Api-Version";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String path = uri.startsWith(context) ? uri.substring(context.length()) : uri;
        if (!path.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        int slash = path.indexOf((char) 47, PREFIX.length());
        String version =
            slash < 0 ? path.substring(PREFIX.length()) : path.substring(PREFIX.length(),
                slash);
        String rest = slash < 0 ? "" : path.substring(slash);
        if (!CURRENT_VERSION.equals(version)) {
            log.warn("拒绝不支持的接口版本 v{}：{}", version, uri);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":404,\"msg\":\"不支持的接口版本 v" + version
                + "，当前仅支持 v" + CURRENT_VERSION + "（路径形如 /monitor/v1/...）\",\"data\":null}");
            return;
        }
        response.setHeader(VERSION_HEADER, CURRENT_VERSION);
        chain.doFilter(new VersionedRequest(request, context + "/monitor" + rest), response);
    }

    /**
     * 把 /monitor/v1/xxx 还原成 /monitor/xxx 的请求包装
     */
    private static final class VersionedRequest extends HttpServletRequestWrapper {

        /** 去掉版本段后的 URI */
        private final String rewritten;

        VersionedRequest(HttpServletRequest request, String rewritten) {
            super(request);
            this.rewritten = rewritten;
        }

        @Override
        public String getRequestURI() {
            return this.rewritten;
        }

        @Override
        public String getServletPath() {
            return this.rewritten;
        }

        @Override
        public StringBuffer getRequestURL() {
            HttpServletRequest request = (HttpServletRequest) this.getRequest();
            StringBuilder builder = new StringBuilder();
            builder.append(request.getScheme()).append("://").append(request.getServerName());
            builder.append((char) 58).append(request.getServerPort()).append(this.rewritten);
            return new StringBuffer(builder.toString());
        }
    }
}
