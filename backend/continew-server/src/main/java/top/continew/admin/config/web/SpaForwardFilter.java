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

package top.continew.admin.config.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;

/**
 * 单页应用（Vue history 模式）的前端路由回退。
 *
 * <p>前端随后端一起发布（见 build.gradle 中 processResources 把 frontend/dist 打进 static/），
 * 而 vue-router 用的是 createWebHistory：直接访问/刷新 {@code /login}、{@code /monitor/device}
 * 这类深链接时，服务端没有同名静态文件，会返回 404 JSON。</p>
 *
 * <p>只拦截「浏览器地址栏导航」：{@code Sec-Fetch-Mode: navigate} 或 {@code Accept} 含
 * {@code text/html}，且末段不带扩展名。接口调用（fetch/XHR）一律放行，未知 URL 仍然走
 * {@code GlobalExceptionHandler} 的「请求 URL 不存在」JSON，语义不变。</p>
 *
 * <p><b>为什么直接写响应体、而不是 forward 到 /index.html：</b>
 * forward 之后 Tomcat 仍以「原始 URI」参与后续拦截器判断（{@code getRequestURI()} 返回的是
 * {@code /login} 而不是 {@code /index.html}），Sa-Token 的 {@code SaInterceptor} 会按
 * {@code /login} 判定未登录并抛 {@code NotLoginException}，浏览器拿到 500。直接输出避免了
 * 二次派发，也让「页面入口不走接口鉴权」这件事变得显式。</p>
 *
 * <p>副作用（已确认可接受）：在浏览器地址栏直接打开 JSON 接口（如
 * {@code /monitor/watchdog/heartbeat}）也会看到 SPA 首页——浏览器导航本就该给页面。</p>
 *
 * @author jdrk
 * @since 2026/11/5
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpaForwardFilter extends OncePerRequestFilter {

    /** 页面入口（jar 内 classpath:/static/index.html） */
    private static final ClassPathResource INDEX = new ClassPathResource("static/index.html");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        if (!this.isDocumentNavigation(request) || this.hasExtension(request) || !INDEX
            .exists()) {
            // 没打进前端产物时（例如只跑了 gradle bootJar 而没跑前端构建）保持原有 404 行为
            chain.doFilter(request, response);
            return;
        }
        response.setContentType("text/html;charset=UTF-8");
        // 入口页必须每次回源，否则发版后浏览器还拿着旧页面对不上新资源哈希
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            response.setContentLengthLong(INDEX.contentLength());
            return;
        }
        try (InputStream in = INDEX.getInputStream()) {
            StreamUtils.copy(in, response.getOutputStream());
        }
    }

    /**
     * 判断是否为浏览器地址栏导航（而非接口调用/静态资源请求）
     *
     * @param request 请求
     * @return 是导航返回 true
     */
    private boolean isDocumentNavigation(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        // 现代浏览器的判据最准：fetch/XHR 是 cors/same-origin，只有地址栏导航才是 navigate
        if ("navigate".equalsIgnoreCase(request.getHeader("Sec-Fetch-Mode"))) {
            return true;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    /**
     * 末段带扩展名（/static/js/x.js、/doc.html、/favicon.ico）一律当作静态资源，不做回退
     *
     * @param request 请求
     * @return 带扩展名返回 true
     */
    private boolean hasExtension(HttpServletRequest request) {
        String path = request.getRequestURI();
        int slash = path.lastIndexOf('/');
        return path.indexOf('.', slash < 0 ? 0 : slash) >= 0;
    }
}
