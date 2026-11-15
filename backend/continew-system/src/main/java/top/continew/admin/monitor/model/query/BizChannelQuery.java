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
 * 通道配置查询条件
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "通道配置查询条件")
public class BizChannelQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关键词
     */
    @Schema(description = "关键词（要素名称、单位或描述）")
    @Query(columns = {"element_name", "unit", "description"}, type = QueryType.LIKE)
    private String description;

    /**
     * 所属设备 ID
     */
    @Schema(description = "所属设备 ID")
    @Query(type = QueryType.EQ)
    private Long deviceId;

    /**
     * 通道号
     */
    @Schema(description = "通道号（1~32）")
    @Query(type = QueryType.EQ)
    private Integer channelNo;

    /**
     * 模拟量序号
     */
    @Schema(description = "模拟量序号（1 或 2）")
    @Query(type = QueryType.EQ)
    private Integer analogNo;

    /**
     * 水质要素类型
     */
    @Schema(description = "水质要素类型（ElementType 枚举名）")
    @Query(type = QueryType.EQ)
    private String elementType;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用")
    @Query(type = QueryType.EQ)
    private Boolean enabled;
}
