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

package top.continew.admin.monitor.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.continew.admin.common.base.model.resp.BaseDetailResp;

import java.io.Serial;

/**
 * 上报规则响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "上报规则响应参数")
public class BizReportRuleResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则名称
     */
    @Schema(description = "规则名称", example = "进水口数据上报")
    private String name;

    /**
     * 上报目标 ID
     */
    @Schema(description = "上报目标 ID", example = "1")
    private Long targetId;

    /**
     * 上报模板 ID
     */
    @Schema(description = "上报模板 ID", example = "1")
    private Long templateId;

    /**
     * 限定端口 ID
     */
    @Schema(description = "限定端口 ID（空表示全部）", example = "1")
    private Long portId;

    /**
     * 限定设备 ID
     */
    @Schema(description = "限定设备 ID（空表示全部）", example = "1")
    private Long deviceId;

    /**
     * 上报间隔（毫秒）
     */
    @Schema(description = "上报间隔（毫秒）", example = "60000")
    private Integer intervalMs;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    /**
     * 描述
     */
    @Schema(description = "描述")
    private String description;
}
