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
 * 自定义帧透传结果（FR-PROTO-08）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "自定义帧透传结果")
public class TransactResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID")
    private Long deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "端口名")
    private String portName;

    @Schema(description = "请求帧（十六进制）")
    private String requestHex;

    @Schema(description = "应答帧（十六进制）")
    private String responseHex;

    @Schema(description = "应答字节数")
    private Integer responseLength;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "耗时（毫秒）")
    private Long durationMs;

    @Schema(description = "本次透传的报文留痕")
    private List<SignalFrameResp> frames = new ArrayList<>();
}
