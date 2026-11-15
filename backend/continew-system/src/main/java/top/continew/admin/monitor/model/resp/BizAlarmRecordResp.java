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
 * 报警记录响应参数
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "报警记录响应参数")
public class BizAlarmRecordResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 命中的规则 ID
     */
    @Schema(description = "命中的规则 ID", example = "1")
    private Long ruleId;

    /**
     * 设备 ID
     */
    @Schema(description = "设备 ID", example = "1")
    private Long deviceId;

    /**
     * 设备名称
     */
    @Schema(description = "设备名称", example = "1 号水质传感器")
    private String deviceName;

    /**
     * 设备地址
     */
    @Schema(description = "设备地址", example = "1")
    private Integer deviceAddr;

    /**
     * 节点编号（即通道号）
     */
    @Schema(description = "节点编号（即通道号）", example = "3")
    private Integer nodeId;

    /**
     * 模拟量序号（1 或 2）
     */
    @Schema(description = "模拟量序号（1 或 2）", example = "1")
    private Integer analogNo;

    /**
     * 要素名称
     */
    @Schema(description = "要素名称", example = "溶解氧")
    private String elementName;

    /**
     * 报警类型
     */
    @Schema(description = "报警类型", example = "LOWER")
    private String alarmType;

    /**
     * 报警信息
     */
    @Schema(description = "报警信息", example = "溶解氧低于下限 5mg/L")
    private String alarmMessage;

    /**
     * 数据序号
     */
    @Schema(description = "数据序号", example = "1")
    private Integer dataNo;

    /**
     * 报警数值
     */
    @Schema(description = "报警数值", example = "4.2")
    private Double dataValue;

    /**
     * 单位
     */
    @Schema(description = "单位", example = "mg/L")
    private String unit;

    /**
     * 报警范围
     */
    @Schema(description = "报警范围", example = "5 ~ 20")
    private String alarmRange;

    /**
     * 记录时间
     */
    @Schema(description = "记录时间", example = "2026-09-23 10:00:00")
    private LocalDateTime recordTime;

    /**
     * 处理标志（0：未处理；1：已处理；2：已忽略）
     */
    @Schema(description = "处理标志（0：未处理；1：已处理；2：已忽略）", example = "0")
    private Integer handlingFlag;

    /**
     * 处理方式
     */
    @Schema(description = "处理方式", example = "已现场核查")
    private String handlingMethod;

    /**
     * 处理人
     */
    @Schema(description = "处理人", example = "张三")
    private String handlingUser;

    /**
     * 处理时间
     */
    @Schema(description = "处理时间", example = "2026-09-23 11:00:00")
    private LocalDateTime handlingTime;

    /**
     * 经度
     */
    @Schema(description = "经度", example = "117.123456")
    private Float lng;

    /**
     * 纬度
     */
    @Schema(description = "纬度", example = "36.654321")
    private Float lat;
}
