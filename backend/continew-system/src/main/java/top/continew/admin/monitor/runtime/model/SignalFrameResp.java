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
import top.continew.admin.monitor.runtime.signal.SignalEntry;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一帧信令的展示模型
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "一帧信令")
public class SignalFrameResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "序号")
    private Long sequence;

    @Schema(description = "时间（毫秒时间戳）")
    private Long timeMillis;

    @Schema(description = "设备 ID")
    private Long deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "从站地址")
    private Integer deviceAddr;

    @Schema(description = "方向（OUT / IN / ERROR）")
    private String direction;

    @Schema(description = "十六进制报文")
    private String frame;

    @Schema(description = "报文字节数")
    private Integer frameLength;

    @Schema(description = "解析结果")
    private String parsed;

    @Schema(description = "耗时（毫秒）")
    private Integer durationMs;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "失败原因")
    private String errorMsg;

    /**
     * 从实时信令转换
     *
     * @param entry 实时信令
     * @return 响应对象
     */
    public static SignalFrameResp of(SignalEntry entry) {
        SignalFrameResp resp = new SignalFrameResp();
        resp.setSequence(entry.sequence());
        resp.setTimeMillis(entry.timeMillis());
        resp.setDeviceId(entry.deviceId());
        resp.setDeviceName(entry.deviceName());
        resp.setDeviceAddr(entry.deviceAddr());
        resp.setDirection(entry.direction());
        resp.setFrame(entry.frame());
        resp.setFrameLength(entry.frameLength());
        resp.setParsed(entry.parsed());
        resp.setDurationMs(entry.durationMs());
        resp.setSuccess(entry.success());
        resp.setErrorMsg(entry.errorMsg());
        return resp;
    }
}
