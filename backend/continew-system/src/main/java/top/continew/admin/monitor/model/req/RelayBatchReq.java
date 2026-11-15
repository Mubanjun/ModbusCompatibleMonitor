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

package top.continew.admin.monitor.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 继电器批量控制请求（FR-RLY-03）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "继电器批量控制请求")
public class RelayBatchReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "各路目标状态（0：断开；1：吸合），按继电器序号顺序", example = "[1, 1, 0, 0]")
    @NotEmpty(message = "批量控制值不能为空")
    private List<Integer> values;
}
