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

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 分组数据权限 handler：仅自研「授权分组 → SQL 条件」这一层。 */
@Component
@RequiredArgsConstructor
public class GroupDataPermissionHandler implements DataPermissionHandler {

    private final JdbcTemplate jdbc;

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        if (mappedStatementId == null
            || !mappedStatementId.startsWith("top.continew.admin.monitor.mapper.")) {
            return null;
        }
        Object loginId;
        try {
            loginId = StpUtil.getLoginId(-1L);
        } catch (Exception e) {
            return null;
        }
        if (!(loginId instanceof Number) || ((Number) loginId).longValue() == 1L) {
            return null;
        }
        long userId = ((Number) loginId).longValue();
        List<Integer> dataScopes = jdbc.queryForList("SELECT r.data_scope FROM sys_role r "
            + "JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ? AND r.deleted = 0",
            Integer.class, userId);
        if (dataScopes.contains(1)) {
            return null;
        }
        Set<Long> allowed = allowedGroups(userId);
        String condition;
        if (mappedStatementId.contains("BizDeviceMapper")
            || mappedStatementId.contains("BizSensorMapper")) {
            condition = allowed.isEmpty() ? "1 = 0" : "group_id IN (" + join(allowed) + ")";
        } else if (mappedStatementId.contains("BizAlarmRecordMapper")
            || mappedStatementId.contains("BizDataMapper")
            || mappedStatementId.contains("BizDataLatestMapper")) {
            condition = allowed.isEmpty() ? "1 = 0"
                : "device_id IN (SELECT id FROM biz_device WHERE deleted = 0 AND group_id IN ("
                    + join(allowed) + "))";
        } else {
            return null;
        }
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (Exception e) {
            throw new IllegalStateException("无法构造分组数据权限条件", e);
        }
    }

    private Set<Long> allowedGroups(long userId) {
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

    private String join(Set<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
