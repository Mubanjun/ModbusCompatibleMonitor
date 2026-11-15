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

package top.continew.admin.jdrk;

import com.mbj.jdcompatiblemonitor.device.ElementRange;
import com.mbj.jdcompatiblemonitor.device.ElementRangeCatalog;
import com.mbj.jdcompatiblemonitor.device.ElementType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水质要素量程字典 API
 * <p>
 * 暴露要素量程与编码方式，便于前端配置设备模板与人工核对。
 * </p>
 *
 * @author jdrk
 * @since 2026/9/22
 */
@Tag(name = "要素量程字典 API")
@RestController
@RequestMapping("/jdrk/elements")
public class ElementCatalogController {

    /**
     * 查询全部要素及其量程
     *
     * @return 要素量程列表
     */
    @Operation(summary = "查询要素量程字典", description = "返回全部水质要素及其量程、编码方式与主机系数")
    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ElementType type : ElementType.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", type.name());
            item.put("name", type.displayName());
            item.put("unit", type.unit());
            List<Map<String, Object>> ranges = new ArrayList<>();
            for (ElementRange range : ElementRangeCatalog.rangesOf(type)) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("model", range.model());
                r.put("label", range.label());
                r.put("min", range.min());
                r.put("max", range.max());
                r.put("encoding", range.encoding().name());
                r.put("encodingDescription", range.encoding().description());
                r.put("coefficientA", range.hostCoefficientA());
                r.put("resolution", range.resolution());
                ranges.add(r);
            }
            item.put("ranges", ranges);
            result.add(item);
        }
        return result;
    }
}
