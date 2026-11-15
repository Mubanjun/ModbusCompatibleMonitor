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

package top.continew.admin.monitor.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 历史数据单点修正请求（FR-WEB-08）
 *
 * <p>按主键 id 修正，或按「设备 + 节点 + 模拟量 + 记录时间」定位；
 * 同时给了 dataValue 与 rawValue 时以 dataValue 为准。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "历史数据单点修正请求")
public class DataCorrectReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "记录 ID（优先按 ID 定位）")
    private Long id;

    @Schema(description = "设备 ID")
    private Long deviceId;

    @Schema(description = "节点号")
    private Integer nodeId;

    @Schema(description = "模拟量序号")
    private Integer analogNo;

    @Schema(description = "记录时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordTime;

    @Schema(description = "修正后的工程值")
    private Double dataValue;

    @Schema(description = "修正后的原始值（只给原始值时按通道系数补算工程值）")
    private Double rawValue;

    @Schema(description = "修正原因（写入操作日志）")
    private String reason;
}
