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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 继电器操作结果（FR-RLY-02/05）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "继电器操作结果")
public class RelayControlResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID")
    private Long deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "继电器序号（批量操作为空）")
    private Integer relayNo;

    @Schema(description = "请求值")
    private String requestValue;

    @Schema(description = "操作前状态")
    private String previousValue;

    @Schema(description = "回读状态")
    private String readbackValue;

    @Schema(description = "回读是否与请求一致")
    private Boolean readbackOk;

    @Schema(description = "执行时长（毫秒，0 表示保持）")
    private Integer durationMs;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "提示信息")
    private String message;

    @Schema(description = "操作时间")
    private LocalDateTime operateTime;

    @Schema(description = "本次操作生成的审计记录 ID")
    private Long logId;

    @Schema(description = "批量操作明细")
    private List<RelayStatusResp.RelayItem> relays = new ArrayList<>();
}
