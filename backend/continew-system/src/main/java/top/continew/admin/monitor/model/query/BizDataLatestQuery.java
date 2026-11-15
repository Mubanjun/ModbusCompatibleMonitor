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

/**
 * 通道实时值查询条件（FR-WEB-01/03/04）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "通道实时值查询条件")
public class BizDataLatestQuery implements Serializable {

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
     * 端口 ID
     */
    @Schema(description = "端口 ID")
    @Query(type = QueryType.EQ)
    private Long portId;

    /**
     * 节点编号
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
     * 设备状态
     */
    @Schema(description = "设备状态（ONLINE / OFFLINE / DISABLED / ALARM / COMM_ERROR / UNKNOWN）")
    @Query(type = QueryType.EQ)
    private String deviceStatus;

    /**
     * 数据质量
     */
    @Schema(description = "数据质量（GOOD / BAD / STALE）")
    @Query(type = QueryType.EQ)
    private String quality;

    /**
     * 是否超量程
     */
    @Schema(description = "是否超量程")
    @Query(type = QueryType.EQ)
    private Boolean overRange;
}
