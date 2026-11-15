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
 * 报警记录查询条件
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "报警记录查询条件")
public class BizAlarmRecordQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关键词
     */
    @Schema(description = "关键词（设备名称、报警信息或要素名称）")
    @Query(columns = {"device_name", "alarm_message", "element_name"}, type = QueryType.LIKE)
    private String description;

    /**
     * 设备 ID
     */
    @Schema(description = "设备 ID", example = "1")
    @Query(type = QueryType.EQ)
    private Long deviceId;

    /**
     * 节点编号
     */
    @Schema(description = "节点编号", example = "3")
    @Query(type = QueryType.EQ)
    private Integer nodeId;

    /**
     * 报警类型
     */
    @Schema(description = "报警类型", example = "LOWER")
    @Query(type = QueryType.EQ)
    private String alarmType;

    /**
     * 处理标志（0：未处理；1：已处理；2：已忽略）
     */
    @Schema(description = "处理标志（0：未处理；1：已处理；2：已忽略）", example = "0")
    @Query(type = QueryType.EQ)
    private Integer handlingFlag;
}
