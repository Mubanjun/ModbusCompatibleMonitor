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

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizReportTargetMapper;
import top.continew.admin.monitor.mapper.BizSyncTaskMapper;
import top.continew.admin.monitor.model.entity.BizSyncTaskDO;
import top.continew.admin.monitor.model.query.BizSyncTaskQuery;
import top.continew.admin.monitor.model.req.BizSyncTaskReq;
import top.continew.admin.monitor.model.resp.BizSyncTaskResp;
import top.continew.admin.monitor.service.BizSyncTaskService;
import top.continew.starter.core.util.validation.CheckUtils;

import java.util.Set;

/**
 * 同步任务业务实现
 *
 * <p>同步任务负责把本系统数据同步到「仁科兼容」目标库（FR-SYNC-01/02），因此需要校验
 * 目标库引用是否存在，并把同步表名限制在仁科脚本的四张表内。
 * 状态与进度计数属运行时字段，由数据库默认值初始化、由同步引擎维护。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
public class BizSyncTaskServiceImpl
    extends
    BaseServiceImpl<BizSyncTaskMapper, BizSyncTaskDO, BizSyncTaskResp, BizSyncTaskResp, BizSyncTaskQuery, BizSyncTaskReq>
    implements BizSyncTaskService {

    /**
     * 仁科兼容目标库的表（见 SRS 6.3）
     */
    private static final Set<String> SUPPORTED_TABLES =
        Set.of("tbalarm", "tbhistory", "tbnoticelog", "tbsyslog");

    private final BizReportTargetMapper bizReportTargetMapper;

    @Override
    public void beforeCreate(BizSyncTaskReq req) {
        this.checkTarget(req.getTargetId());
        this.checkTableNames(req.getTableNames());
        this.checkNameRepeat(req.getName(), null);
    }

    @Override
    public void beforeUpdate(BizSyncTaskReq req, Long id) {
        this.checkTarget(req.getTargetId());
        this.checkTableNames(req.getTableNames());
        this.checkNameRepeat(req.getName(), id);
    }

    /**
     * 校验目标库存在
     *
     * @param targetId 目标库 ID
     */
    private void checkTarget(Long targetId) {
        CheckUtils.throwIf(bizReportTargetMapper.selectById(targetId) == null, "所选目标库不存在");
    }

    /**
     * 校验同步表名都在仁科脚本的四张表内
     *
     * @param tableNames 逗号分隔的表名
     */
    private void checkTableNames(String tableNames) {
        if (ObjectUtil.isEmpty(tableNames)) {
            return;
        }
        for (String tableName : StrUtil.splitTrim(tableNames, ',')) {
            CheckUtils.throwIf(!SUPPORTED_TABLES.contains(tableName.toLowerCase()),
                "不支持的同步表：[{}]，仅支持 tbalarm、tbhistory、tbnoticelog、tbsyslog", tableName);
        }
    }

    /**
     * 检查任务名称是否重复
     *
     * @param name 任务名称
     * @param id   ID
     */
    private void checkNameRepeat(String name, Long id) {
        CheckUtils.throwIf(baseMapper.lambdaQuery()
            .eq(BizSyncTaskDO::getName, name)
            .ne(id != null, BizSyncTaskDO::getId, id)
            .exists(), "任务名称 [{}] 已存在", name);
    }
}
