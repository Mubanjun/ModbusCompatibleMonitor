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
 * 继电器状态（FR-RLY-01）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "继电器状态")
public class RelayStatusResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID")
    private Long deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "设备地址")
    private Integer deviceAddr;

    @Schema(description = "端口地址")
    private String portName;

    @Schema(description = "是否在线（本次读取是否成功）")
    private Boolean online;

    @Schema(description = "继电器寄存器起始地址")
    private Integer relayRegBase;

    @Schema(description = "继电器路数")
    private Integer relayCount;

    @Schema(description = "原始寄存器值")
    private List<Integer> registers = new ArrayList<>();

    @Schema(description = "继电器状态")
    private List<RelayItem> relays = new ArrayList<>();

    @Schema(description = "失败原因")
    private String errorMsg;

    /**
     * 单路继电器状态
     */
    @Data
    @Schema(description = "单路继电器状态")
    public static class RelayItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "继电器序号（1 起）")
        private Integer relayNo;

        @Schema(description = "寄存器地址")
        private Integer register;

        @Schema(description = "是否吸合")
        private Boolean closed;

        @Schema(description = "状态说明")
        private String label;
    }
}
