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
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.monitor.model.req.BizDeviceGroupReq;
import top.continew.admin.monitor.model.resp.BizDeviceGroupNode;
import top.continew.admin.monitor.service.BizDeviceGroupService;

/** 设备分组树 API。 */
@Tag(name = "设备分组树 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/device-group")
public class BizDeviceGroupController {

    private final BizDeviceGroupService groupService;

    @GetMapping("/tree")
    @SaCheckPermission("device:group:list")
    public List<BizDeviceGroupNode> tree() {
        return groupService.tree();
    }

    @PostMapping
    @SaCheckPermission("device:group:create")
    public Long create(@Valid @RequestBody BizDeviceGroupReq req) {
        return groupService.create(req);
    }

    @PutMapping("/{id}")
    @SaCheckPermission("device:group:update")
    public void update(@PathVariable Long id, @Valid @RequestBody BizDeviceGroupReq req) {
        groupService.update(id, req);
    }

    @PostMapping("/{id}/devices/move")
    @SaCheckPermission("device:group:update")
    public void moveDevices(@PathVariable Long id, @RequestBody List<Long> deviceIds) {
        groupService.moveDevices(deviceIds, id);
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("device:group:delete")
    public void delete(@PathVariable Long id,
        @RequestParam(defaultValue = "MOVE_TO_PARENT") String strategy) {
        groupService.delete(id, strategy);
    }
}
