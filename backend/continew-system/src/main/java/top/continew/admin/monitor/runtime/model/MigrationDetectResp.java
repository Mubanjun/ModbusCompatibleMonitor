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
import java.util.ArrayList;
import java.util.List;

/**
 * 源库探测结果（FR-IO-06 检测原库）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "源库探测结果")
public class MigrationDetectResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否连接成功")
    private Boolean connected;

    @Schema(description = "源库名")
    private String database;

    @Schema(description = "表数量")
    private Integer tableCount;

    @Schema(description = "识别到的仁科标准表（DR-03）")
    private List<String> dr03Tables = new ArrayList<>();

    @Schema(description = "疑似设备/通道配置表（含设备相关列）")
    private List<String> deviceTables = new ArrayList<>();

    @Schema(description = "各表行数")
    private List<TableRow> tables = new ArrayList<>();

    @Schema(description = "错误信息")
    private String errorMsg;

    /**
     * 表行数
     */
    @Data
    @Schema(description = "表行数")
    public static class TableRow implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "表名")
        private String name;

        @Schema(description = "行数")
        private Long rows;

        @Schema(description = "是否 DR-03 标准表")
        private Boolean dr03;

        @Schema(description = "是否疑似设备配置表")
        private Boolean deviceTable;
    }
}
