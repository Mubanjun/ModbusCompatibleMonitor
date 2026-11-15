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

/**
 * 日志级别响应（FR-LOG-01）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "日志级别响应")
public class LogLevelResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "日志器名称（ROOT 表示根日志器）")
    private String logger;

    @Schema(description = "当前生效级别")
    private String level;

    @Schema(description = "是否受平台管理（可在线切换）")
    private Boolean managed;
}
