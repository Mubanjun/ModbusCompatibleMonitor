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
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.monitor.mapper.BizAlarmRecordMapper;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.model.entity.BizAlarmRecordDO;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.query.BizAlarmRecordQuery;
import top.continew.admin.monitor.model.resp.BizAlarmRecordResp;
import top.continew.admin.monitor.runtime.alarm.AlarmRecordCommand;
import top.continew.admin.monitor.service.BizAlarmRecordService;
import top.continew.starter.core.util.validation.CheckUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 报警记录业务实现
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
// @formatter:off
// 泛型参数拼接后单行会超出 Checkstyle 的 150 字符上限，而 Spotless 不会拆分泛型，故此处手工换行并豁免格式化
public class BizAlarmRecordServiceImpl
    extends BaseServiceImpl<BizAlarmRecordMapper, BizAlarmRecordDO, BizAlarmRecordResp, BizAlarmRecordResp, BizAlarmRecordQuery,
        BizAlarmRecordResp>
    implements BizAlarmRecordService {
    // @formatter:on

    /** 处理标志：未处理 */
    private static final int FLAG_PENDING = 0;
    /** 处理标志：已处理 */
    private static final int FLAG_HANDLED = 1;

    private final BizDeviceMapper bizDeviceMapper;

    @Override
    public Long record(AlarmRecordCommand command) {
        BizAlarmRecordDO entity = new BizAlarmRecordDO();
        entity.setCreateUser(0L);
        entity.setRuleId(command.ruleId());
        entity.setDeviceId(command.deviceId());
        entity.setDeviceName(command.deviceName());
        entity.setDeviceAddr(command.deviceAddr());
        entity.setNodeId(command.channelNo());
        entity.setAnalogNo(command.analogNo());
        entity.setElementName(command.elementName());
        entity.setAlarmType(command.alarmType());
        entity.setAlarmMessage(command.alarmMessage());
        entity.setDataNo(command.channelNo());
        entity.setDataValue(command.dataValue());
        entity.setUnit(command.unit());
        entity.setAlarmRange(command.alarmRange());
        entity.setRecordTime(this.toLocalDateTime(command.eventTime()));
        if (command.recovered()) {
            // 恢复记录视为已自动处理，避免污染待处理队列
            entity.setHandlingFlag(FLAG_HANDLED);
            entity.setHandlingMethod("自动恢复");
            entity.setHandlingTime(this.toLocalDateTime(command.eventTime()));
        } else {
            entity.setHandlingFlag(FLAG_PENDING);
        }
        BizDeviceDO device = bizDeviceMapper.selectById(command.deviceId());
        if (device != null) {
            if (StrUtil.isNotBlank(device.getName())) {
                entity.setDeviceName(device.getName());
            }
            if (device.getLongitude() != null) {
                entity.setLng(device.getLongitude().floatValue());
            }
            if (device.getLatitude() != null) {
                entity.setLat(latitude(device));
            }
        }
        this.save(entity);
        return entity.getId();
    }

    @Override
    public boolean handle(Long id, Integer handlingFlag, String handlingMethod) {
        CheckUtils.throwIf(id == null, "报警记录 ID 不能为空");
        int flag = handlingFlag == null ? FLAG_HANDLED : handlingFlag;
        CheckUtils.throwIf(flag != FLAG_HANDLED && flag != 2, "处理标志只能为 1 或 2");
        BizAlarmRecordDO entity = this.getById(id);
        CheckUtils.throwIf(entity == null, "报警记录不存在");
        entity.setHandlingFlag(flag);
        entity.setHandlingMethod(handlingMethod);
        entity.setHandlingUser(UserContextHolder.getUsername());
        entity.setHandlingTime(LocalDateTime.now());
        return this.updateById(entity);
    }

    private Float latitude(BizDeviceDO device) {
        return device.getLatitude() == null ? null : device.getLatitude().floatValue();
    }

    private LocalDateTime toLocalDateTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
