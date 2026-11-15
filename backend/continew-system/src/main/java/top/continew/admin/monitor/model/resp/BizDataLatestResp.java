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
 * 通道实时值响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "通道实时值响应参数")
public class BizDataLatestResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 设备 ID
     */
    @Schema(description = "设备 ID")
    private Long deviceId;

    /**
     * 设备名称
     */
    @Schema(description = "设备名称")
    private String deviceName;

    /**
     * 设备地址
     */
    @Schema(description = "设备地址（从站地址）")
    private Integer deviceAddr;

    /**
     * 端口 ID
     */
    @Schema(description = "端口 ID")
    private Long portId;

    /**
     * 节点编号
     */
    @Schema(description = "节点编号（通道号）")
    private Integer nodeId;

    /**
     * 模拟量序号
     */
    @Schema(description = "模拟量序号（1 或 2）")
    private Integer analogNo;

    /**
     * 要素类型
     */
    @Schema(description = "要素类型")
    private String elementType;

    /**
     * 要素名称
     */
    @Schema(description = "要素名称")
    private String elementName;

    /**
     * 工程值
     */
    @Schema(description = "工程值", example = "7.42")
    private Double dataValue;

    /**
     * 原始值
     */
    @Schema(description = "原始值", example = "742")
    private Double rawValue;

    /**
     * 单位
     */
    @Schema(description = "单位", example = "pH")
    private String unit;

    /**
     * 设备状态
     */
    @Schema(description = "设备状态", example = "ONLINE")
    private String deviceStatus;

    /**
     * 数据质量
     */
    @Schema(description = "数据质量", example = "GOOD")
    private String quality;

    /**
     * 是否超量程
     */
    @Schema(description = "是否超量程", example = "false")
    private Boolean overRange;

    /**
     * 采集时间
     */
    @Schema(description = "采集时间")
    private LocalDateTime collectTime;
}
