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
 * 单通道通讯测试明细
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "单通道通讯测试明细")
public class ChannelTestResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "通道号")
    private Integer channelNo;

    @Schema(description = "寄存器起始地址")
    private Integer regAddr;

    @Schema(description = "原始寄存器值")
    private List<Integer> registers = new ArrayList<>();

    @Schema(description = "解析出的模拟量")
    private List<AnalogTestResp> samples = new ArrayList<>();

    @Schema(description = "失败原因")
    private String errorMsg;
}
