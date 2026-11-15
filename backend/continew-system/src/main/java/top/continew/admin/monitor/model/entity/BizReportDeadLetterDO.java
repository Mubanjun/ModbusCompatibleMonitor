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
 * 上报死信实体（FR-RPT-05）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_report_dead_letter")
public class BizReportDeadLetterDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 上报目标 ID
     */
    private Long targetId;

    /**
     * 上报规则 ID
     */
    private Long ruleId;

    /**
     * 目标名称
     */
    private String targetName;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 目标类型
     */
    private String targetType;

    /**
     * 报文格式
     */
    private String formatType;

    /**
     * 失败时的报文内容
     */
    private String payload;

    /**
     * 报文包含的数据条数
     */
    private Integer itemCount;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 状态（DEAD / REPLAYED / REPLAY_FAILED）
     */
    private String state;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 首次失败时间
     */
    private LocalDateTime firstFailTime;

    /**
     * 最近重放时间
     */
    private LocalDateTime replayTime;

    /**
     * 重放结果说明
     */
    private String replayResult;
}
