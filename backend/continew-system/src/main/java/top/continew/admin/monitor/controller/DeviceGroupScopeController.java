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

package top.continew.admin.monitor.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 角色授权分组范围 API。 */
@Tag(name = "角色设备数据范围 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/device-group/roles")
public class DeviceGroupScopeController {

    private final JdbcTemplate jdbc;

    /** 查询角色授权分组。 */
    @GetMapping("/{roleId}/scopes")
    @SaCheckPermission("device:group:list")
    public List<Long> scopes(@PathVariable Long roleId) {
        return jdbc.queryForList(
            "SELECT group_id FROM biz_role_group_scope WHERE role_id = ? ORDER BY group_id",
            Long.class, roleId);
    }

    /** 保存角色授权分组。 */
    @PutMapping("/{roleId}/scopes")
    @SaCheckPermission("device:group:update")
    @Transactional
    public void save(@PathVariable Long roleId, @RequestBody List<Long> groupIds) {
        jdbc.update("DELETE FROM biz_role_group_scope WHERE role_id = ?", roleId);
        for (Long groupId : groupIds) {
            jdbc.update(
                "INSERT INTO biz_role_group_scope (role_id, group_id, create_user) VALUES (?, ?, 0)",
                roleId,
                groupId);
        }
    }
}
