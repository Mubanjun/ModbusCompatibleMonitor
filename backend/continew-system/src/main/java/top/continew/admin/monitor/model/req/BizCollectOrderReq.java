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
 * 采集顺序创建或修改请求参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "采集顺序创建或修改请求参数")
public class BizCollectOrderReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属端口 ID
     */
    @Schema(description = "所属端口 ID", example = "1")
    @NotNull(message = "所属端口不能为空")
    private Long portId;

    /**
     * 设备 ID
     */
    @Schema(description = "设备 ID", example = "1")
    @NotNull(message = "设备不能为空")
    private Long deviceId;

    /**
     * 采集顺序
     */
    @Schema(description = "采集顺序（数字越小越先轮询）", example = "1")
    @Min(value = 0, message = "采集顺序不能小于 {value}")
    @Max(value = 9999, message = "采集顺序不能大于 {value}")
    private Integer sortNo;

    /**
     * 是否参与轮询
     */
    @Schema(description = "是否参与轮询", example = "true")
    @NotNull(message = "是否参与轮询不能为空")
    private Boolean enabled;
}
