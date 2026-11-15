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

package top.continew.admin.monitor.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.continew.starter.data.annotation.Query;
import top.continew.starter.data.enums.QueryType;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 采集历史数据查询条件（FR-WEB-05：按设备/节点/时间范围/是否报警查询与分页）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "采集历史数据查询条件")
public class BizDataQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关键词
     */
    @Schema(description = "关键词（设备名称或要素名称）")
    @Query(columns = {"device_name", "element_name"}, type = QueryType.LIKE)
    private String description;

    /**
     * 设备 ID
     */
    @Schema(description = "设备 ID")
    @Query(type = QueryType.EQ)
    private Long deviceId;

    /**
     * 设备地址
     */
    @Schema(description = "设备地址（从站地址）")
    @Query(type = QueryType.EQ)
    private Integer deviceAddr;

    /**
     * 节点编号（通道号）
     */
    @Schema(description = "节点编号（通道号）")
    @Query(type = QueryType.EQ)
    private Integer nodeId;

    /**
     * 模拟量序号
     */
    @Schema(description = "模拟量序号（1 或 2）")
    @Query(type = QueryType.EQ)
    private Integer analogNo;

    /**
     * 要素类型
     */
    @Schema(description = "要素类型")
    @Query(type = QueryType.EQ)
    private String elementType;

    /**
     * 是否报警
     */
    @Schema(description = "是否报警（0：否；1：是）")
    @Query(type = QueryType.EQ)
    private Integer alarmFlag;

    /**
     * 起始时间
     */
    @Schema(description = "起始时间（含）")
    @Query(columns = "record_time", type = QueryType.GE)
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间（含）")
    @Query(columns = "record_time", type = QueryType.LE)
    private LocalDateTime endTime;

    /**
     * 是否按当前通道系数重算工程值（FR-WEB-08，仅影响展示、不落库）
     */
    @Schema(description = "是否按当前通道系数重算工程值（仅展示，不落库）")
    private Boolean recalculate;

    /**
     * 聚合粒度（MINUTE / HOUR / DAY，为空表示不聚合）
     */
    @Schema(description = "聚合粒度（MINUTE / HOUR / DAY）")
    private String granularity;
}
