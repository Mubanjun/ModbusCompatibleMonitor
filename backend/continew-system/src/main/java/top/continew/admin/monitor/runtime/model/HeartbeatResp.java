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

package top.continew.admin.monitor.runtime.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 后端心跳（FR-WD-02）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "后端心跳")
public class HeartbeatResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "进程名")
    private String processName;

    @Schema(description = "进程号")
    private Long pid;

    @Schema(description = "心跳时间")
    private String timestamp;

    @Schema(description = "启动时长（毫秒）")
    private Long uptimeMs;

    @Schema(description = "整体是否健康")
    private Boolean healthy;

    @Schema(description = "检查项")
    private List<SelfCheckItemResp> checks = new ArrayList<>();
}
