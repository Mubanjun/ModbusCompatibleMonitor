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

package top.continew.admin.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNodeConfig;
import cn.hutool.core.lang.tree.TreeUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.auth.LoginHandler;
import top.continew.admin.auth.LoginHandlerFactory;
import top.continew.admin.auth.constant.AuthConstants;
import top.continew.admin.auth.enums.AuthTypeEnum;
import top.continew.admin.auth.model.req.LoginReq;
import top.continew.admin.auth.model.resp.LoginResp;
import top.continew.admin.auth.model.resp.RouteResp;
import top.continew.admin.auth.service.AuthService;
import top.continew.admin.common.context.RoleContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.system.constant.SystemConstants;
import top.continew.admin.system.enums.MenuTypeEnum;
import top.continew.admin.system.model.entity.user.UserDO;
import top.continew.admin.system.model.resp.ClientResp;
import top.continew.admin.system.model.resp.MenuResp;
import top.continew.admin.system.service.ClientService;
import top.continew.admin.system.service.MenuService;
import top.continew.admin.system.service.RoleService;
import top.continew.admin.system.service.UserService;
import top.continew.starter.cache.redisson.util.RedisUtils;
import top.continew.starter.core.util.validation.CheckUtils;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.extension.crud.annotation.TreeField;
import top.continew.starter.extension.crud.autoconfigure.CrudProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 认证业务实现
 *
 * @author Charles7c
 * @since 2022/12/21 21:49
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 令牌中的客户端 ID 附加信息键（与 AbstractLoginHandler#CLIENT_ID 保持一致） */
    private static final String EXTRA_CLIENT_ID = "clientId";
    /** 票据里用户名与客户端之间的分隔符 */
    private static final String TICKET_SEPARATOR = ":";

    private final LoginHandlerFactory loginHandlerFactory;
    private final ClientService clientService;
    private final RoleService roleService;
    private final MenuService menuService;
    private final UserService userService;
    private final CrudProperties crudProperties;

    @Override
    public LoginResp login(LoginReq req, HttpServletRequest request) {
        AuthTypeEnum authType = req.getAuthType();
        // 校验客户端
        ClientResp client = clientService.getByClientId(req.getClientId());
        ValidationUtils.throwIfNull(client, "客户端不存在");
        ValidationUtils.throwIf(DisEnableStatusEnum.DISABLE.equals(client.getStatus()), "客户端已禁用");
        ValidationUtils.throwIf(!client.getAuthType().contains(authType.getValue()),
            "该客户端暂未授权 [{}] 认证", authType
                .getDescription());
        // 获取处理器
        LoginHandler<LoginReq> loginHandler = loginHandlerFactory.getHandler(authType);
        // 登录前置处理
        loginHandler.preLogin(req, client, request);
        // 登录
        LoginResp loginResp = loginHandler.login(req, client, request);
        // 登录后置处理
        loginHandler.postLogin(req, client, request);
        return loginResp;
    }

    @Override
    public String createOneTimeTicket() {
        Long userId = StpUtil.getLoginIdAsLong();
        // 客户端标识优先取当前请求上下文：jwt-simple 模式下令牌是无状态的，
        // StpUtil.getExtra 读不到登录时写入的 extra（这正是票据登录报「客户端不存在」的原因）
        String clientId = null;
        if (UserContextHolder.getContext() != null) {
            clientId = UserContextHolder.getContext().getClientId();
        }
        if (StrUtil.isBlank(clientId)) {
            clientId = Convert.toStr(StpUtil.getExtra(EXTRA_CLIENT_ID), null);
        }
        CheckUtils.throwIf(StrUtil.isBlank(clientId), "无法确定当前登录的客户端，请重新登录后再试");
        String ticket = RandomUtil.randomString(48);
        RedisUtils.set(AuthConstants.ONE_TIME_TICKET_PREFIX + ticket, userId + TICKET_SEPARATOR
            + clientId, Duration.ofSeconds(AuthConstants.ONE_TIME_TICKET_TTL_SECONDS));
        log.info("已签发一次性登录票据（用户 {}，有效期 {} 秒）", userId,
            AuthConstants.ONE_TIME_TICKET_TTL_SECONDS);
        return ticket;
    }

    @Override
    public LoginResp ticketLogin(String ticket, HttpServletRequest request) {
        CheckUtils.throwIf(ticket == null || ticket.isBlank(), "票据不能为空");
        String key = AuthConstants.ONE_TIME_TICKET_PREFIX + ticket.trim();
        String payload = RedisUtils.get(key);
        // 一次性语义：先取再删，只有 delete 成功的请求算消费成功（并发下仅一个请求能通过）
        boolean consumed = RedisUtils.delete(key);
        CheckUtils.throwIf(payload == null || !consumed, "一次性票据无效或已被使用");
        String[] parts = payload.split(TICKET_SEPARATOR, 2);
        Long userId = Convert.toLong(parts[0]);
        CheckUtils.throwIfNull(userId, "票据内容不合法");
        UserDO user = userService.getById(userId);
        CheckUtils.throwIfNull(user, "票据对应的用户不存在");
        String clientId = parts.length > 1 && !parts[1].isBlank() ? parts[1] : null;
        ClientResp client = clientId == null ? null : clientService.getByClientId(clientId);
        ValidationUtils.throwIfNull(client, "票据对应的客户端不存在");
        ValidationUtils.throwIf(DisEnableStatusEnum.DISABLE.equals(client.getStatus()), "客户端已禁用");
        LoginHandler<LoginReq> loginHandler = loginHandlerFactory.getHandler(AuthTypeEnum.ACCOUNT);
        LoginResp loginResp = loginHandler.loginWithoutPassword(user, client, request);
        log.warn("一次性票据登录成功（用户 {}，客户端 {}）", userId, clientId);
        return loginResp;
    }

    @Override
    public List<RouteResp> buildRouteTree(Long userId) {
        Set<RoleContext> roleSet = roleService.listByUserId(userId);
        if (CollUtil.isEmpty(roleSet)) {
            return new ArrayList<>(0);
        }
        // 查询菜单列表
        Set<MenuResp> menuSet = new LinkedHashSet<>();
        if (roleSet.stream().anyMatch(r -> SystemConstants.SUPER_ADMIN_ROLE_ID.equals(r.getId()))) {
            menuSet.addAll(menuService.listByRoleId(SystemConstants.SUPER_ADMIN_ROLE_ID));
        } else {
            roleSet.forEach(r -> menuSet.addAll(menuService.listByRoleId(r.getId())));
        }
        List<MenuResp> menuList =
            menuSet.stream().filter(m -> !MenuTypeEnum.BUTTON.equals(m.getType())).toList();
        if (CollUtil.isEmpty(menuList)) {
            return new ArrayList<>(0);
        }
        // 构建路由树
        TreeField treeField = MenuResp.class.getDeclaredAnnotation(TreeField.class);
        TreeNodeConfig treeNodeConfig =
            crudProperties.getTreeDictModel().genTreeNodeConfig(treeField);
        List<Tree<Long>> treeList =
            TreeUtil.build(menuList, treeField.rootId(), treeNodeConfig, (m, tree) -> {
                tree.setId(m.getId());
                tree.setParentId(m.getParentId());
                tree.setName(m.getTitle());
                tree.setWeight(m.getSort());
                tree.putExtra("type", m.getType().getValue());
                tree.putExtra("path", m.getPath());
                tree.putExtra("name", m.getName());
                tree.putExtra("component", m.getComponent());
                tree.putExtra("redirect", m.getRedirect());
                tree.putExtra("icon", m.getIcon());
                tree.putExtra("isExternal", m.getIsExternal());
                tree.putExtra("isCache", m.getIsCache());
                tree.putExtra("isHidden", m.getIsHidden());
                tree.putExtra("permission", m.getPermission());
            });
        return BeanUtil.copyToList(treeList, RouteResp.class);
    }
}
