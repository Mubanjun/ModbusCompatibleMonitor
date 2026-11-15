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
 * 采集内核运行状态（FR-QT-01 / FR-ACQ-10 的可视化数据源）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "采集内核运行状态")
public class AcquisitionStatusResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否正在采集")
    private Boolean running;

    @Schema(description = "是否全局暂停")
    private Boolean paused;

    @Schema(description = "启动时间（毫秒时间戳）")
    private Long startedAt;

    @Schema(description = "持续运行时长（毫秒）")
    private Long uptimeMs;

    @Schema(description = "上次配置加载时间（毫秒时间戳）")
    private Long lastReloadTime;

    @Schema(description = "轮询轮次")
    private Long roundCount;

    @Schema(description = "设备轮询次数")
    private Long devicePollCount;

    @Schema(description = "产出的样本条数")
    private Long sampleCount;

    @Schema(description = "信令开关")
    private Boolean traceEnabled;

    @Schema(description = "信令丢弃条数")
    private Long traceDroppedCount;

    @Schema(description = "当前生效的采集参数")
    private AcquisitionSettingsResp settings;

    @Schema(description = "最近一次错误")
    private String lastError;

    @Schema(description = "端口运行状态")
    private List<PortStatusResp> ports = new ArrayList<>();
}
