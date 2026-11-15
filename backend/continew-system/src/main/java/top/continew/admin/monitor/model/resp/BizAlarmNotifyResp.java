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
 * 报警通知响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "报警通知响应参数")
public class BizAlarmNotifyResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 报警记录 ID
     */
    @Schema(description = "报警记录 ID", example = "1")
    private Long recordId;

    /**
     * 通知方式（MQTT / EMAIL / SMS）
     */
    @Schema(description = "通知方式（MQTT / EMAIL / SMS）", example = "EMAIL")
    private String notifyType;

    /**
     * 通知目标（主题/邮箱/手机号）
     */
    @Schema(description = "通知目标（主题/邮箱/手机号）", example = "ops@example.com")
    private String target;

    /**
     * 通知内容
     */
    @Schema(description = "通知内容", example = "溶解氧低于下限")
    private String content;

    /**
     * 发送状态（0：待发送；1：成功；2：失败）
     */
    @Schema(description = "发送状态（0：待发送；1：成功；2：失败）", example = "1")
    private Integer sendStatus;

    /**
     * 重试次数
     */
    @Schema(description = "重试次数", example = "0")
    private Integer retryCount;

    /**
     * 发送时间
     */
    @Schema(description = "发送时间", example = "2026-09-23 10:00:05")
    private LocalDateTime sendTime;

    /**
     * 失败原因
     */
    @Schema(description = "失败原因", example = "SMTP 连接超时")
    private String errorMsg;
}
