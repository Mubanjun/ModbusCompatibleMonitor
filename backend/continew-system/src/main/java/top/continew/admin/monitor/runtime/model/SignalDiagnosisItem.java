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
 * 单条信令诊断建议（FR-SIG-04）
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Data
@Schema(description = "信令诊断建议")
public class SignalDiagnosisItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "现象代码", example = "TIMEOUT")
    private String code;

    @Schema(description = "现象名称", example = "读取超时")
    private String title;

    @Schema(description = "命中条数")
    private Integer count;

    @Schema(description = "诊断建议（对应处理动作）")
    private String suggestion;

    @Schema(description = "样例错误信息")
    private String sample;
}
