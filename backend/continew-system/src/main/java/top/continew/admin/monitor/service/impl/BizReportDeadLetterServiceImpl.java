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

package top.continew.admin.monitor.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizReportDeadLetterMapper;
import top.continew.admin.monitor.model.entity.BizReportDeadLetterDO;
import top.continew.admin.monitor.model.query.BizReportDeadLetterQuery;
import top.continew.admin.monitor.model.req.BizReportDeadLetterReq;
import top.continew.admin.monitor.model.resp.BizReportDeadLetterResp;
import top.continew.admin.monitor.runtime.report.ReportReplayService;
import top.continew.admin.monitor.service.BizReportDeadLetterService;
import top.continew.starter.core.util.validation.CheckUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 上报死信业务实现
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
// @formatter:off
// 泛型参数拼接后单行会超出 Checkstyle 的 150 字符上限，而 Spotless 不会拆分泛型，故此处手工换行并豁免格式化
public class BizReportDeadLetterServiceImpl
    extends BaseServiceImpl<BizReportDeadLetterMapper, BizReportDeadLetterDO, BizReportDeadLetterResp,
        BizReportDeadLetterResp, BizReportDeadLetterQuery, BizReportDeadLetterReq>
    implements BizReportDeadLetterService {
    // @formatter:on

    /** 状态：死信 */
    public static final String STATE_DEAD = "DEAD";
    /** 状态：重放成功 */
    public static final String STATE_REPLAYED = "REPLAYED";
    /** 状态：重放失败 */
    public static final String STATE_REPLAY_FAILED = "REPLAY_FAILED";

    private final ReportReplayService reportReplayService;

    @Override
    public String replay(List<Long> ids) {
        CheckUtils.throwIf(ids == null || ids.isEmpty(), "请选择要重放的死信");
        List<String> messages = new ArrayList<>();
        int success = 0;
        for (Long id : ids) {
            BizReportDeadLetterDO entity = this.getById(id);
            if (entity == null) {
                messages.add(id + "：记录不存在");
                continue;
            }
            String error = reportReplayService.replay(entity);
            entity.setReplayTime(LocalDateTime.now());
            if (error == null) {
                entity.setState(STATE_REPLAYED);
                entity.setReplayResult("重放成功");
                success++;
            } else {
                entity.setState(STATE_REPLAY_FAILED);
                entity.setReplayResult(StrUtil.maxLength(error, 480));
            }
            this.updateById(entity);
            messages.add(id + "：" + entity.getReplayResult());
        }
        return "共 " + ids.size() + " 条，成功 " + success + " 条；" + String.join("；", messages);
    }

    @Override
    public int purgeOverflow(long keep) {
        return baseMapper.purgeOverflow(Math.max(keep, 1L));
    }
}
