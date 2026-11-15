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

/**
 * 历史数据修正结果（FR-WEB-08）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "历史数据修正结果")
public class DataCorrectResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "记录 ID")
    private Long id;

    @Schema(description = "设备名")
    private String deviceName;

    @Schema(description = "节点号")
    private Integer nodeId;

    @Schema(description = "模拟量序号")
    private Integer analogNo;

    @Schema(description = "记录时间")
    private String recordTime;

    @Schema(description = "修正前工程值")
    private Double beforeValue;

    @Schema(description = "修正后工程值")
    private Double afterValue;

    @Schema(description = "修正前原始值")
    private Double beforeRawValue;

    @Schema(description = "修正后原始值")
    private Double afterRawValue;

    @Schema(description = "是否更新了实时值缓存")
    private Boolean latestRefreshed;

    @Schema(description = "说明")
    private String message;
}
