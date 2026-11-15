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
 * 信令记录响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "信令记录响应参数")
public class BizSignalLogResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 端口 ID
     */
    @Schema(description = "端口 ID")
    private Long portId;

    /**
     * 端口地址
     */
    @Schema(description = "端口地址", example = "COM1")
    private String portName;

    /**
     * 设备 ID
     */
    @Schema(description = "设备 ID")
    private Long deviceId;

    /**
     * 从站地址
     */
    @Schema(description = "从站地址", example = "1")
    private Integer deviceAddr;

    /**
     * 方向
     */
    @Schema(description = "方向（OUT：发送；IN：接收；ERROR：异常）", example = "OUT")
    private String direction;

    /**
     * 十六进制报文
     */
    @Schema(description = "十六进制报文", example = "01 03 00 00 00 02 C4 0B")
    private String frame;

    /**
     * 报文字节数
     */
    @Schema(description = "报文字节数", example = "8")
    private Integer frameLength;

    /**
     * 解析结果
     */
    @Schema(description = "解析结果", example = "通道 1 模拟量2=742")
    private String parsed;

    /**
     * 耗时（毫秒）
     */
    @Schema(description = "耗时（毫秒）", example = "35")
    private Integer durationMs;

    /**
     * 是否成功
     */
    @Schema(description = "是否成功", example = "true")
    private Boolean success;

    /**
     * 失败原因
     */
    @Schema(description = "失败原因")
    private String errorMsg;

    /**
     * 报文时间
     */
    @Schema(description = "报文时间", example = "2026-09-23 10:00:00")
    private LocalDateTime traceTime;
}
