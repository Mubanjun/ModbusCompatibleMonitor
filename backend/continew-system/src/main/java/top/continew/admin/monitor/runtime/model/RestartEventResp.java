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
 * 看门狗重启事件（FR-WD-03/05）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "看门狗重启事件")
public class RestartEventResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "事件时间")
    private String time;

    @Schema(description = "事件类型（HEARTBEAT_TIMEOUT / CRASH / RESTART / REBOOT_LIMIT）")
    private String eventType;

    @Schema(description = "重启原因")
    private String reason;

    @Schema(description = "累计重启次数")
    private Integer restartCount;

    @Schema(description = "新进程号")
    private Long pid;
}
