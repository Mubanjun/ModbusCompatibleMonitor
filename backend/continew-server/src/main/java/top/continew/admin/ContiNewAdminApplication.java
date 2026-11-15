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

package top.continew.admin;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alicp.jetcache.anno.config.EnableMethodCache;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.core.ContiNewStarterVersion;
import top.continew.starter.core.autoconfigure.application.ApplicationProperties;
import top.continew.starter.extension.crud.annotation.EnableCrudApi;
import top.continew.starter.web.annotation.EnableGlobalResponse;
import top.continew.starter.web.model.R;
import top.nextdoc4j.core.configuration.NextDoc4jProperties;

/**
 * 启动程序
 *
 * @author Charles7c
 * @since 2022/12/8 23:15
 */
@Slf4j
@EnableCrudApi
@RestController
@SpringBootApplication
@RequiredArgsConstructor
@EnableScheduling
@EnableAsync(proxyTargetClass = true)
@EnableGlobalResponse
@EnableFeignClients
@MapperScan(basePackages = {"top.continew.admin.**.mapper"})
@EnableMethodCache(basePackages = {"top.continew.admin"})
public class ContiNewAdminApplication implements ApplicationRunner {

    private final ApplicationProperties applicationProperties;
    // 用 ObjectProvider 而不是直接注入：离线工具（--spring.main.web-application-type=none）
    // 没有 Web 环境，直接注入 ServerProperties 会让「离线重置密码」这类应急入口根本起不来
    private final ObjectProvider<ServerProperties> serverPropertiesProvider;

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ContiNewAdminApplication.class);
        application.setDefaultProperties(
            MapUtil.of("continew-starter.version", ContiNewStarterVersion.getVersion()));
        application.run(args);
    }

    @Hidden
    @SaIgnore
    @GetMapping("/")
    public R index() {
        return R.ok(applicationProperties);
    }

    @Override
    public void run(ApplicationArguments args) {
        String hostAddress = NetUtil.getLocalhostStr();
        ServerProperties serverProperties = serverPropertiesProvider.getIfAvailable();
        String baseUrl;
        if (serverProperties == null) {
            // 非 Web 模式（离线工具）：没有服务地址可展示
            baseUrl = "-（未启动 Web 服务）";
        } else {
            Integer port = serverProperties.getPort();
            String contextPath = serverProperties.getServlet().getContextPath();
            baseUrl = URLUtil.normalize("%s:%s%s".formatted(hostAddress, port, contextPath));
        }
        log.info("--------------------------------------------------------");
        log.info("{} server started successfully.", applicationProperties.getName());
        log.info("ContiNew Starter: v{} (Spring Boot: v{})", ContiNewStarterVersion.getVersion(),
            SpringBootVersion
                .getVersion());
        log.info("当前版本: v{} (Profile: {})", applicationProperties.getVersion(), SpringUtil
            .getProperty("spring.profiles.active"));
        log.info("服务地址: {}", baseUrl);
        try {
            NextDoc4jProperties docProperties = SpringUtil.getBean(NextDoc4jProperties.class);
            if (!docProperties.isProduction()) {
                log.info("接口文档: {}/doc.html", baseUrl);
            }
        } catch (Exception e) {
            log.debug("非 Web 模式，跳过接口文档地址输出");
        }
        log.info("吐槽广场: https://continew.top/docs/admin/issue-hub.html");
        log.info("常见问题: https://continew.top/docs/admin/faq.html");
        log.info("更新日志: https://continew.top/docs/admin/changelog/");
        log.info("ContiNew Admin: 持续迭代优化的，高质量多租户中后台管理系统框架");
        log.info("--------------------------------------------------------");
    }
}
