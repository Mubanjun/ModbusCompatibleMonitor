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
import java.util.List;

/**
 * 串口自动探测请求（FR-ACQ-06/07）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "串口自动探测请求")
public class SerialProbeReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "端口 ID")
    @NotNull(message = "端口 ID 不能为空")
    private Long portId;

    @Schema(description = "已知从站地址（可选，波特率探测时使用；不传用 1）")
    @Min(value = 1, message = "从站地址最小为 1")
    @Max(value = 247, message = "从站地址最大为 247")
    private Integer address;

    @Schema(description = "候选波特率（可选，默认 1200~115200 常用档）")
    private List<Integer> baudRates;

    @Schema(description = "从站地址扫描上限（默认 32）")
    @Min(value = 1, message = "扫描上限最小为 1")
    @Max(value = 247, message = "扫描上限最大为 247")
    private Integer maxAddress;
}
