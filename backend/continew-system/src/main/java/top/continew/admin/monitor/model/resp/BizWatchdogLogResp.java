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

package top.continew.admin.monitor.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.continew.admin.common.base.model.resp.BaseDetailResp;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 看门狗日志响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "看门狗日志响应参数")
public class BizWatchdogLogResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 被监控进程名
     */
    @Schema(description = "被监控进程名", example = "jdrk-backend")
    private String processName;

    /**
     * 事件类型（HEARTBEAT_TIMEOUT / CRASH / RESTART / SELF_CHECK / REBOOT_LIMIT）
     */
    @Schema(description = "事件类型（HEARTBEAT_TIMEOUT / CRASH / RESTART / SELF_CHECK / REBOOT_LIMIT）",
        example = "RESTART")
    private String eventType;

    /**
     * 事件时间
     */
    @Schema(description = "事件时间", example = "2026-09-23 10:00:00")
    private LocalDateTime eventTime;

    /**
     * 事件详情
     */
    @Schema(description = "事件详情", example = "超过 120 秒未收到心跳，判定失联并重启")
    private String detail;

    /**
     * 累计重启次数
     */
    @Schema(description = "累计重启次数", example = "1")
    private Integer restartCount;
}
