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

package top.continew.admin.monitor.service;

import top.continew.admin.common.base.service.BaseService;
import top.continew.admin.monitor.model.entity.BizAlarmRecordDO;
import top.continew.admin.monitor.model.query.BizAlarmRecordQuery;
import top.continew.admin.monitor.model.resp.BizAlarmRecordResp;
import top.continew.admin.monitor.runtime.alarm.AlarmRecordCommand;
import top.continew.starter.data.service.IService;

/**
 * 报警记录业务接口（FR-ALM-02/03）
 *
 * <p>运行时数据，控制器只开放列表、详情与处理登记，不提供自由增删改。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
public interface BizAlarmRecordService extends
    BaseService<BizAlarmRecordResp, BizAlarmRecordResp, BizAlarmRecordQuery, BizAlarmRecordResp>,
    IService<BizAlarmRecordDO> {

    /**
     * 写入一条报警记录
     *
     * @param command 报警记录参数
     * @return 记录 ID
     */
    Long record(AlarmRecordCommand command);

    /**
     * 登记报警处理结果（FR-ALM-03：确认、处理方式、处理人、处理时间）
     *
     * @param id             记录 ID
     * @param handlingFlag   处理标志（1：已处理；2：已忽略）
     * @param handlingMethod 处理方式
     * @return 是否成功
     */
    boolean handle(Long id, Integer handlingFlag, String handlingMethod);
}
