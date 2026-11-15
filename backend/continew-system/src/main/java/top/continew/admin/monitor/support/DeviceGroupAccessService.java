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

package top.continew.admin.monitor.support;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 设备分组访问守卫：供详情/修改/删除等单对象接口做显式 403 判断。 */
@Service
@RequiredArgsConstructor
public class DeviceGroupAccessService {

    private final JdbcTemplate jdbc;

    /** 当前用户是否为超管。 */
    public boolean isAdmin() {
        try {
            Object loginId = StpUtil.getLoginId(-1L);
            return loginId instanceof Number && ((Number) loginId).longValue() == 1L;
        } catch (Exception e) {
            return true;
        }
    }

    /** 当前用户可见分组集合（含子分组）。 */
    public Set<Long> allowedGroupIds() {
        if (isAdmin()) {
            return Set.of();
        }
        long userId = ((Number) StpUtil.getLoginId(-1L)).longValue();
        List<Integer> dataScopes = jdbc.queryForList("SELECT r.data_scope FROM sys_role r "
            + "JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ? AND r.deleted = 0",
            Integer.class,
            userId);
        if (dataScopes.contains(1)) {
            return Set.of();
        }
        List<Long> scopes = jdbc.queryForList(
            "SELECT DISTINCT s.group_id FROM biz_role_group_scope s "
                + "JOIN sys_user_role ur ON ur.role_id = s.role_id WHERE ur.user_id = ?",
            Long.class, userId);
        if (scopes.isEmpty()) {
            return Set.of();
        }
        Set<Long> scopeIds = new HashSet<>(scopes);
        List<Map<String, Object>> groups = jdbc.queryForList(
            "SELECT id, path FROM biz_device_group WHERE deleted = 0");
        Set<String> paths = groups.stream()
            .filter(g -> scopeIds.contains(((Number) g.get("id")).longValue()))
            .map(g -> String.valueOf(g.get("path")))
            .collect(Collectors.toSet());
        return groups.stream()
            .filter(g -> {
                long id = ((Number) g.get("id")).longValue();
                String path = String.valueOf(g.get("path"));
                return scopeIds.contains(id) || paths.stream().anyMatch(path::startsWith);
            })
            .map(g -> ((Number) g.get("id")).longValue())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 断言分组可访问。 */
    public void assertAllowed(Long groupId) {
        if (isAdmin()) {
            return;
        }
        Set<Long> allowed = allowedGroupIds();
        if (!allowed.contains(groupId)) {
            throw new NotPermissionException("无权访问该分组数据");
        }
    }

    /** 断言设备集合可访问。 */
    public void assertDevicesAllowed(Collection<Long> deviceIds) {
        if (isAdmin() || deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        Set<Long> allowed = allowedGroupIds();
        if (allowed.isEmpty()) {
            throw new NotPermissionException("无权访问设备数据");
        }
        String placeholders = deviceIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Long> groups =
            jdbc.queryForList("SELECT group_id FROM biz_device WHERE deleted = 0 AND id IN ("
                + placeholders + ")", Long.class, deviceIds.toArray());
        if (groups.stream().anyMatch(g -> !allowed.contains(g))) {
            throw new NotPermissionException("无权访问设备数据");
        }
    }
}
