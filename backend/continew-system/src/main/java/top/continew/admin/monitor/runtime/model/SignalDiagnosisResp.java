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
 * 信令诊断结果（FR-SIG-04）
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Data
@Schema(description = "信令诊断结果")
public class SignalDiagnosisResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID（为空表示按端口/全局统计）")
    private Long deviceId;

    @Schema(description = "端口 ID")
    private Long portId;

    @Schema(description = "参与分析的最近信令条数")
    private Integer sampled;

    @Schema(description = "其中失败条数")
    private Integer failed;

    @Schema(description = "成功率（%）")
    private Double successRate;

    @Schema(description = "诊断建议（按现象分组）")
    private List<SignalDiagnosisItem> items = new ArrayList<>();
}
