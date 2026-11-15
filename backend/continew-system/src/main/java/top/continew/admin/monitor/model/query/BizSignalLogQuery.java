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
 * 信令记录查询条件（FR-SIG-02：按端口、设备、方向、关键字、时间过滤）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "信令记录查询条件")
public class BizSignalLogQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关键词
     */
    @Schema(description = "关键词（报文、解析结果或端口地址）")
    @Query(columns = {"frame", "parsed", "port_name"}, type = QueryType.LIKE)
    private String description;

    /**
     * 端口 ID
     */
    @Schema(description = "端口 ID")
    @Query(type = QueryType.EQ)
    private Long portId;

    /**
     * 设备 ID
     */
    @Schema(description = "设备 ID")
    @Query(type = QueryType.EQ)
    private Long deviceId;

    /**
     * 方向
     */
    @Schema(description = "方向（OUT：发送；IN：接收；ERROR：异常）")
    @Query(type = QueryType.EQ)
    private String direction;

    /**
     * 是否成功
     */
    @Schema(description = "是否成功")
    @Query(type = QueryType.EQ)
    private Boolean success;

    /**
     * 起始时间
     */
    @Schema(description = "起始时间（含）")
    @Query(columns = "trace_time", type = QueryType.GE)
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间（含）")
    @Query(columns = "trace_time", type = QueryType.LE)
    private LocalDateTime endTime;
}
