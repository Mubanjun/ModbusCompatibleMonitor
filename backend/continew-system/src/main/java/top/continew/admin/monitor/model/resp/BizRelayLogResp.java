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
 * 继电器操作日志响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "继电器操作日志响应参数")
public class BizRelayLogResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID")
    private Long deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "设备地址")
    private Integer deviceAddr;

    @Schema(description = "端口 ID")
    private Long portId;

    @Schema(description = "继电器序号")
    private Integer relayNo;

    @Schema(description = "动作")
    private String action;

    @Schema(description = "请求值")
    private String requestValue;

    @Schema(description = "操作前状态")
    private String previousValue;

    @Schema(description = "回读状态")
    private String readbackValue;

    @Schema(description = "回读是否与请求一致")
    private Boolean readbackOk;

    @Schema(description = "执行时长（毫秒）")
    private Integer durationMs;

    @Schema(description = "结果")
    private String resultStatus;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "操作时间")
    private LocalDateTime operateTime;

    @Schema(description = "操作人 ID")
    private Long operatorId;

    @Schema(description = "操作人名称")
    private String operatorName;
}
