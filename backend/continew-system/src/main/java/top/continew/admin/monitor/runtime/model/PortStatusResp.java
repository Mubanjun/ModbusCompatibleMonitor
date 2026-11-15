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
 * 端口运行状态
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "端口运行状态")
public class PortStatusResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "端口 ID")
    private Long id;

    @Schema(description = "端口名称")
    private String name;

    @Schema(description = "端口类型（SERIAL / TCP）")
    private String portType;

    @Schema(description = "端口地址")
    private String portName;

    @Schema(description = "是否已连接")
    private Boolean connected;

    @Schema(description = "是否暂停")
    private Boolean paused;

    @Schema(description = "本轮是否仍在执行")
    private Boolean busy;

    @Schema(description = "设备数量")
    private Integer deviceCount;

    @Schema(description = "最近错误")
    private String lastError;

    @Schema(description = "设备运行状态")
    private List<DeviceStatusResp> devices = new ArrayList<>();
}
