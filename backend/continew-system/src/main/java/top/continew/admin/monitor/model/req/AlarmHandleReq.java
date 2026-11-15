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
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 报警处理登记请求参数（FR-ALM-03）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "报警处理登记请求参数")
public class AlarmHandleReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 处理标志
     */
    @Schema(description = "处理标志（1：已处理；2：已忽略）", example = "1")
    @NotNull(message = "处理标志不能为空")
    @Min(value = 1, message = "处理标志只能为 1 或 2")
    @Max(value = 2, message = "处理标志只能为 1 或 2")
    private Integer handlingFlag;

    /**
     * 处理方式
     */
    @Schema(description = "处理方式说明", example = "已现场核查，清洗探头")
    @Length(max = 2000, message = "处理方式长度不能超过 {max} 个字符")
    private String handlingMethod;
}
