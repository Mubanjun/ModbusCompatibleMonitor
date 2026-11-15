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

package top.continew.admin.monitor.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.BaseDO;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 同步任务实体
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_sync_task")
public class BizSyncTaskDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 目标库（上报目标 ID）
     */
    private Long targetId;

    /**
     * 同步模式（FULL：全量；INCREMENT：增量）
     */
    private String syncMode;

    /**
     * 冲突策略（NEWER_WINS / OVERWRITE / SKIP / MANUAL）
     */
    private String conflictPolicy;

    /**
     * 同步的表名（逗号分隔）
     */
    private String tableNames;

    /**
     * 状态（IDLE / RUNNING / PAUSED / DONE / FAILED）
     */
    private String taskStatus;

    /**
     * 上次同步时间
     */
    private LocalDateTime lastSyncTime;

    /**
     * 总记录数
     */
    private Long totalCount;

    /**
     * 成功数
     */
    private Long successCount;

    /**
     * 失败数
     */
    private Long failCount;

    /**
     * 最近错误
     */
    private String errorMsg;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 描述
     */
    private String description;
}
