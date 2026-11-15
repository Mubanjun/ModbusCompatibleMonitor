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
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量校时请求（NFR-REL-06）
 *
 * @author jdrk
 * @since 2026/10/1
 */
@Data
@Schema(description = "批量校时请求")
public class TimeSyncBatchReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID 列表，为空表示全部启用设备", example = "[1, 2, 3]")
    private List<Long> deviceIds;

    @Schema(description = "校时方式（ALL：全部下发，默认；TOLERANT：仅偏差超出容差的设备下发）",
        example = "ALL")
    @Pattern(regexp = "(?i)ALL|TOLERANT", message = "校时方式只能为 ALL 或 TOLERANT")
    private String mode;
}
