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
 * 采集历史数据响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "采集历史数据响应参数")
public class BizDataResp extends BaseDetailResp {

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
    @Schema(description = "设备名称", example = "1 号水质传感器")
    private String deviceName;

    /**
     * 设备地址
     */
    @Schema(description = "设备地址（从站地址）", example = "1")
    private Integer deviceAddr;

    /**
     * 节点编号（通道号）
     */
    @Schema(description = "节点编号（通道号）", example = "1")
    private Integer nodeId;

    /**
     * 模拟量序号
     */
    @Schema(description = "模拟量序号（1 或 2）", example = "2")
    private Integer analogNo;

    /**
     * 要素类型
     */
    @Schema(description = "要素类型", example = "PH")
    private String elementType;

    /**
     * 要素名称
     */
    @Schema(description = "要素名称", example = "pH")
    private String elementName;

    /**
     * 工程值
     */
    @Schema(description = "工程值", example = "7.42")
    private Double dataValue;

    /**
     * 原始值
     */
    @Schema(description = "原始值（未乘系数 A/B）", example = "742")
    private Double rawValue;

    /**
     * 单位
     */
    @Schema(description = "单位", example = "pH")
    private String unit;

    /**
     * 是否报警
     */
    @Schema(description = "是否报警（0：否；1：是）", example = "0")
    private Integer alarmFlag;

    /**
     * 记录时间
     */
    @Schema(description = "记录时间", example = "2026-09-23 10:00:00")
    private LocalDateTime recordTime;

    /**
     * 来源暂存记录 ID
     */
    @Schema(description = "来源暂存记录 ID")
    private Long sourceId;

    /**
     * 本行工程值是否为按当前系数重算的结果
     */
    @Schema(description = "本行工程值是否为按当前系数重算的结果", example = "false")
    private Boolean recalculated;
}
