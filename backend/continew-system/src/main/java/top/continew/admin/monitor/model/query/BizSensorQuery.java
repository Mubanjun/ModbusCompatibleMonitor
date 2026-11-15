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
import java.io.Serializable;
import lombok.Data;
import top.continew.starter.data.annotation.Query;
import top.continew.starter.data.enums.QueryType;

/** 传感器对象查询条件。 */
@Data
@Schema(description = "传感器对象查询条件")
public class BizSensorQuery implements Serializable {

    @Schema(description = "关键词（名称/要素编码）")
    @Query(columns = {"name", "element_code"}, type = QueryType.LIKE)
    private String description;

    @Schema(description = "所属分组 ID")
    @Query(type = QueryType.EQ)
    private Long groupId;

    @Schema(description = "所属设备 ID")
    @Query(type = QueryType.EQ)
    private Long deviceId;

    @Schema(description = "要素编码")
    @Query(type = QueryType.EQ)
    private String elementCode;

    @Schema(description = "是否启用")
    @Query(type = QueryType.EQ)
    private Boolean enabled;
}
