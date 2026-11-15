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
 * 同步水位实体
 *
 * <p>运行时生成数据，仅提供查询。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_sync_watermark")
public class BizSyncWatermarkDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标库 ID
     */
    private Long targetId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 上次同步到的记录时间
     */
    private LocalDateTime lastRecordTime;

    /**
     * 上次同步到的主键
     */
    private String lastId;
}
