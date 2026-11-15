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

package top.continew.admin.monitor.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import top.continew.admin.monitor.model.entity.BizReportDeadLetterDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.time.LocalDateTime;

/**
 * 上报死信 Mapper
 *
 * @author jdrk
 * @since 2026/9/23
 */
public interface BizReportDeadLetterMapper extends BaseMapper<BizReportDeadLetterDO> {

    /**
     * 物理清理超容量或过期的死信（避免无界增长）
     *
     * @param keep 保留条数
     * @return 删除行数
     */
    @Delete("DELETE FROM biz_report_dead_letter WHERE id NOT IN"
        + " (SELECT id FROM (SELECT id FROM biz_report_dead_letter ORDER BY id DESC LIMIT #{keep}) t)")
    int purgeOverflow(@Param("keep") long keep);

    /**
     * 物理清理指定时间之前的死信
     *
     * @param deadline 时间界限
     * @return 删除行数
     */
    @Delete("DELETE FROM biz_report_dead_letter WHERE create_time < #{deadline}")
    int purgeBefore(@Param("deadline") LocalDateTime deadline);
}
