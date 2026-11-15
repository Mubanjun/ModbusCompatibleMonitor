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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单路继电器控制请求（FR-RLY-02）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "单路继电器控制请求")
public class RelayControlReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "继电器序号（1 起）", example = "3")
    @NotNull(message = "继电器序号不能为空")
    @Min(value = 1, message = "继电器序号最小为 1")
    @Max(value = 32, message = "继电器序号最大为 32")
    private Integer relayNo;

    @Schema(description = "是否吸合（true：闭合；false：断开）", example = "true")
    @NotNull(message = "目标状态不能为空")
    private Boolean closed;

    @Schema(description = "执行时长（毫秒，0 或空表示保持）", example = "0")
    @Min(value = 0, message = "执行时长不能小于 {value}")
    private Integer durationMs;
}
