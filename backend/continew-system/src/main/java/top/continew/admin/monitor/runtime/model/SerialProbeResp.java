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
 * 串口自动探测结果（FR-ACQ-06/07）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "串口自动探测结果")
public class SerialProbeResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "端口名")
    private String portName;

    @Schema(description = "探测到的波特率（未命中为空）")
    private Integer baudRate;

    @Schema(description = "探测到的从站地址")
    private List<Integer> addresses = new ArrayList<>();

    @Schema(description = "是否复用了正在采集的端口会话（复用时不扫描波特率）")
    private Boolean reused = Boolean.FALSE;

    @Schema(description = "尝试的波特率档数")
    private Integer baudAttempts;

    @Schema(description = "尝试的地址数")
    private Integer addressAttempts;

    @Schema(description = "耗时（毫秒）")
    private Long durationMs;

    @Schema(description = "说明")
    private String note;
}
