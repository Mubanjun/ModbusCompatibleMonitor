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

package top.continew.admin.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import top.continew.admin.system.mapper.user.UserMapper;
import top.continew.admin.system.model.entity.user.UserDO;
import top.continew.starter.core.util.validation.CheckUtils;
import top.continew.starter.extension.tenant.util.TenantUtils;

/**
 * 离线重置超管密码（FR-AUTH-04）。
 *
 * <p>复用 Spring 上下文而不是自己连库拼 SQL：sys_user.password 带字段加密注解
 * （FieldEncrypt + PasswordEncoderEncryptor），密文既过 BCrypt 又走字段加密，
 * 离线复刻这套算法容易出错。用法（Web 用 0 随机端口，执行完立即退出）：</p>
 *
 * <pre>
 * java -jar continew-admin.jar --server.port=0 \
 *      --jdrk.tool.reset-password=admin --jdrk.tool.reset-password-value='新密码'
 * </pre>
 *
 * <p>不要用 --spring.main.web-application-type=none：项目里有若干必须 Web 上下文的 Bean
 * （springdoc 等），非 Web 模式根本起不来；随机端口同样不会影响正在运行的服务。</p>
 *
 * <p>多租户部署可用 --jdrk.tool.reset-password-tenant=&lt;租户 ID&gt; 指定目标租户（默认 0）。</p>
 *
 * <p>安全提示：本入口是应急通道，会绕过密码策略与登录校验，用后请立即删除命令历史，
 * 并尽快在页面上改回强密码。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jdrk.tool", name = "reset-password")
public class PasswordResetRunner implements ApplicationRunner {

    private final UserMapper userMapper;

    @Value("${jdrk.tool.reset-password}")
    private String username;

    @Value("${jdrk.tool.reset-password-value}")
    private String newPassword;

    /** 目标租户（多租户部署时指定，默认 0） */
    @Value("${jdrk.tool.reset-password-tenant:0}")
    private Long tenantId;

    /**
     * 执行离线重置
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            CheckUtils.throwIf(username == null || username.isBlank(), "必须指定要重置的用户名");
            CheckUtils.throwIf(newPassword == null || newPassword.isBlank(), "必须指定新密码");
            // 离线工具没有请求上下文：多租户插件会拼出 tenant_id = NULL，
            // 结果既查不到用户也改不动数据（实测失败信息是「用户不存在」）。
            // 显式放在目标租户上下文里执行。
            TenantUtils.execute(tenantId, () -> {
                UserDO user = userMapper.lambdaQuery()
                    .eq(UserDO::getUsername, username)
                    .last("LIMIT 1")
                    .one();
                CheckUtils.throwIf(user == null, "用户不存在：{}", username);
                UserDO update = new UserDO();
                update.setId(user.getId());
                // 这里必须写明文：password 字段带 FieldEncrypt(PasswordEncoderEncryptor)，
                // 真正的 BCrypt 由字段加密器在写库时执行。自己再 encode 一次会变成「两次哈希」，
                // 结果是「重置成功」但新密码永远登不进去（本项目第一个版本就踩了这个坑）。
                update.setPassword(newPassword);
                userMapper.updateById(update);
            });
            log.warn("离线重置完成：用户 {} 的密码已更新（请尽快登录后改回强密码）", username);
            System.exit(0);
        } catch (Exception e) {
            log.error("离线重置失败：{}", e.getMessage());
            System.exit(1);
        }
    }
}
