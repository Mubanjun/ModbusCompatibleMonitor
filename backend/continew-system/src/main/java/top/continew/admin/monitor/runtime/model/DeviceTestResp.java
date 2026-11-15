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
 * 设备通讯测试结果（FR-DEV-13：显示报文与解析结果）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "设备通讯测试结果")
public class DeviceTestResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID")
    private Long deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "从站地址")
    private Integer slaveAddr;

    @Schema(description = "协议")
    private String protocol;

    @Schema(description = "端口地址")
    private String portName;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "耗时（毫秒）")
    private Long durationMs;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "通道测试明细")
    private List<ChannelTestResp> channels = new ArrayList<>();

    @Schema(description = "本次测试的报文留痕")
    private List<SignalFrameResp> frames = new ArrayList<>();
}
