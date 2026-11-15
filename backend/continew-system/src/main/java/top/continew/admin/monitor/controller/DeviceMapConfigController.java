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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.monitor.mapper.BizSysConfigMapper;
import top.continew.admin.monitor.model.entity.BizSysConfigDO;

/** 地图运行期配置：Key 不进入前端构建产物。 */
@Tag(name = "设备地图配置 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/device-map")
public class DeviceMapConfigController {

    private final BizSysConfigMapper configMapper;

    /** 返回地图运行期配置。 */
    @GetMapping("/config")
    @SaCheckPermission("device:map:view")
    public Map<String, Object> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean enabled = false;
        String key = "";
        String securityCode = "";
        for (BizSysConfigDO c : configMapper.selectList(new LambdaQueryWrapper<BizSysConfigDO>()
            .eq(BizSysConfigDO::getCategory, "MAP")
            .eq(BizSysConfigDO::getDeleted, 0))) {
            if ("AMAP_ENABLED".equals(c.getCode())) {
                enabled = "1".equals(c.getValue()) || "true".equalsIgnoreCase(c.getValue());
            } else if ("AMAP_JS_KEY".equals(c.getCode())) {
                key = c.getValue();
            } else if ("AMAP_SECURITY_CODE".equals(c.getCode())) {
                securityCode = c.getValue();
            }
        }
        result.put("enabled", enabled && !key.isBlank());
        result.put("key", key);
        result.put("securityCode", securityCode);
        result.put("coordinateSystem", "WGS84");
        result.put("renderCoordinateSystem", "GCJ02");
        return result;
    }
}
