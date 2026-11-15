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
 * 看门狗日志查询条件
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "看门狗日志查询条件")
public class BizWatchdogLogQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关键词
     */
    @Schema(description = "关键词（进程名或事件详情）")
    @Query(columns = {"process_name", "detail"}, type = QueryType.LIKE)
    private String description;

    /**
     * 被监控进程名
     */
    @Schema(description = "被监控进程名", example = "jdrk-backend")
    @Query(type = QueryType.EQ)
    private String processName;

    /**
     * 事件类型
     */
    @Schema(description = "事件类型", example = "RESTART")
    @Query(type = QueryType.EQ)
    private String eventType;
}
