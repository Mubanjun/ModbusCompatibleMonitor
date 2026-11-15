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
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 自定义帧透传请求（FR-PROTO-08）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "自定义帧透传请求")
public class TransactReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "待发送帧（十六进制，可带空格）", example = "01 03 00 00 00 02")
    @NotBlank(message = "待发送帧不能为空")
    private String frame;

    @Schema(description = "期望应答字节数，0 或空表示不读取应答", example = "0")
    private Integer expectedLength;
}
