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

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 前端静态资源映射（现场只发一个 jar，界面由后端 8000 端口直接托管）。
 *
 * <p>前端产物由 build.gradle 的 processResources 从 {@code frontend/dist} 并入
 * {@code classpath:/static/}，落盘后的布局是：</p>
 *
 * <pre>
 * classpath:/static/index.html
 * classpath:/static/favicon.ico、logo.svg
 * classpath:/static/static/js|css|images/…   （dist 的 static/ 目录）
 * classpath:/static/assets/…
 * </pre>
 *
 * <p>这里显式注册映射，而不是依赖 Spring Boot 默认的 {@code /**} → {@code classpath:/static/}：
 * 实测该默认映射在本工程里没有生效（{@code /index.html} 直接落到
 * 「请求 URL 不存在」的 NoHandlerFoundException），显式注册不受
 * {@code spring.web.resources.add-mappings} 开关影响，行为可预期。</p>
 *
 * <p>刻意<b>不</b>注册 {@code /**} 兜底：那会把未知接口路径也交给资源处理器，
 * 使 404 由 NoHandlerFoundException 变成 NoResourceFoundException，
 * 破坏接口「请求 URL 不存在」的既有语义。</p>
 *
 * @author jdrk
 * @since 2026/11/5
 */
@Configuration
public class WebStaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // index.html 里引用的是 /static/js、/static/css、/static/images，对应 jar 内 static/static/
        registry.addResourceHandler("/static/**")
            .addResourceLocations("classpath:/static/static/");
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/assets/");
        registry.addResourceHandler("/index.html", "/favicon.ico", "/logo.svg")
            .addResourceLocations("classpath:/static/");
    }
}
