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

import java.io.Serial;
import java.io.Serializable;

/**
 * 历史数据聚合结果（FR-WEB-05 的「聚合」与 FR-WEB-02 曲线取数）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "历史数据聚合结果")
public class DataAggregateResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 时间桶
     */
    @Schema(description = "时间桶", example = "2026-09-23 10:00")
    private String bucket;

    /**
     * 平均值
     */
    @Schema(description = "平均值", example = "7.42")
    private Double avgValue;

    /**
     * 最小值
     */
    @Schema(description = "最小值", example = "7.40")
    private Double minValue;

    /**
     * 最大值
     */
    @Schema(description = "最大值", example = "7.45")
    private Double maxValue;

    /**
     * 样本条数
     */
    @Schema(description = "样本条数", example = "12")
    private Long sampleCount;
}
