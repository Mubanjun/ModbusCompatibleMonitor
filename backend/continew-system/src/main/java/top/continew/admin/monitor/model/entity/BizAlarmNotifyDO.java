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
 * 报警通知实体
 *
 * <p>运行时生成数据，仅提供查询。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_alarm_notify")
public class BizAlarmNotifyDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 报警记录 ID
     */
    private Long recordId;

    /**
     * 通知方式（MQTT / EMAIL / SMS）
     */
    private String notifyType;

    /**
     * 通知目标（主题/邮箱/手机号）
     */
    private String target;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 发送状态（0：待发送；1：成功；2：失败）
     */
    private Integer sendStatus;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 失败原因
     */
    private String errorMsg;
}
