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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 同步任务创建或修改请求参数
 *
 * <p>只包含配置项：状态、上次同步时间、进度计数与错误信息均由同步引擎在运行时维护，
 * 不通过本接口写入。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "同步任务创建或修改请求参数")
public class BizSyncTaskReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务名称
     */
    @Schema(description = "任务名称", example = "历史数据同步至仁科库")
    @NotBlank(message = "任务名称不能为空")
    @Length(max = 50, message = "任务名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 目标库 ID
     */
    @Schema(description = "目标库（上报目标 ID）", example = "1")
    @NotNull(message = "目标库不能为空")
    private Long targetId;

    /**
     * 同步模式
     */
    @Schema(description = "同步模式（FULL：全量；INCREMENT：增量）", example = "INCREMENT")
    @NotBlank(message = "同步模式不能为空")
    @Pattern(regexp = "FULL|INCREMENT", message = "同步模式只能为 FULL 或 INCREMENT")
    private String syncMode;

    /**
     * 冲突策略
     */
    @Schema(description = "冲突策略（NEWER_WINS：新数据优先；OVERWRITE：覆盖；SKIP：跳过；MANUAL：人工确认）",
        example = "NEWER_WINS")
    @NotBlank(message = "冲突策略不能为空")
    @Pattern(regexp = "NEWER_WINS|OVERWRITE|SKIP|MANUAL",
        message = "冲突策略只能为 NEWER_WINS、OVERWRITE、SKIP 或 MANUAL")
    private String conflictPolicy;

    /**
     * 同步的表名
     */
    @Schema(description = "同步的表名（逗号分隔，对齐仁科脚本）",
        example = "tbalarm,tbhistory,tbnoticelog,tbsyslog")
    @Length(max = 500, message = "表名长度不能超过 {max} 个字符")
    private String tableNames;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用", example = "true")
    @NotNull(message = "是否启用不能为空")
    private Boolean enabled;

    /**
     * 描述
     */
    @Schema(description = "描述")
    @Length(max = 200, message = "描述长度不能超过 {max} 个字符")
    private String description;
}
