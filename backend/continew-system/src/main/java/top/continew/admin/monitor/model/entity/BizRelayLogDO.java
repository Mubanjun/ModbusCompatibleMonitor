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
 * 继电器操作审计实体（FR-RLY-04）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_relay_log")
public class BizRelayLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 设备 ID
     */
    private Long deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备地址
     */
    private Integer deviceAddr;

    /**
     * 端口 ID
     */
    private Long portId;

    /**
     * 继电器序号（批量操作为空）
     */
    private Integer relayNo;

    /**
     * 动作（CLOSE / OPEN / BATCH / AUTO_RESTORE）
     */
    private String action;

    /**
     * 请求值
     */
    private String requestValue;

    /**
     * 操作前状态
     */
    private String previousValue;

    /**
     * 回读状态
     */
    private String readbackValue;

    /**
     * 回读是否与请求一致
     */
    private Boolean readbackOk;

    /**
     * 执行时长（毫秒，0 表示保持）
     */
    private Integer durationMs;

    /**
     * 结果（SUCCESS / FAILED / REJECTED）
     */
    private String resultStatus;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

    /**
     * 操作人 ID（系统自动恢复为空）
     */
    private Long operatorId;

    /**
     * 操作人名称
     */
    private String operatorName;
}
