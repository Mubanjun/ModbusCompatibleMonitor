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
 * 同步冲突实体
 *
 * <p>运行时生成数据，仅提供查询。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_sync_conflict")
public class BizSyncConflictDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 同步任务 ID
     */
    private Long taskId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 记录主键
     */
    private String recordId;

    /**
     * 来源记录时间
     */
    private LocalDateTime sourceTime;

    /**
     * 目标记录时间
     */
    private LocalDateTime targetTime;

    /**
     * 最终保留的是否为来源数据
     */
    private Boolean keptSource;

    /**
     * 应用的冲突策略
     */
    private String policy;

    /**
     * 冲突详情
     */
    private String detail;
}
