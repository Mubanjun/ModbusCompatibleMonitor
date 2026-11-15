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

package top.continew.admin.monitor.runtime.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 看门狗状态（FR-WD-02/05/06）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Schema(description = "看门狗状态")
public class WatchdogStatusResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "被监控进程名")
    private String processName;

    @Schema(description = "心跳文件路径")
    private String heartbeatFile;

    @Schema(description = "看门狗日志路径")
    private String watchdogLog;

    @Schema(description = "最近一次心跳（来自心跳文件）")
    private String lastHeartbeat;

    @Schema(description = "最近心跳距今秒数")
    private Long heartbeatAgeSeconds;

    @Schema(description = "心跳缺失时的明确文案")
    private String heartbeatMessage;

    @Schema(description = "心跳是否超时（FR-WD-02 失联判定）")
    private Boolean heartbeatStale;

    @Schema(description = "心跳超时阈值（秒）")
    private Integer heartbeatTimeoutSeconds;

    @Schema(description = "最近一次完整自检结论")
    private List<SelfCheckItemResp> selfCheck = new ArrayList<>();

    @Schema(description = "最近若干次重启事件")
    private List<RestartEventResp> restarts = new ArrayList<>();

    @Schema(description = "最近一小时重启次数")
    private Integer restartCountLastHour;

    /**
     * 是否启用多实例（集群）模式（NFR-SCALE-04）
     */
    private Boolean clusterEnabled;

    /**
     * 本实例集群角色：SINGLE / LEADER / FOLLOWER
     */
    private String clusterRole;

    /**
     * 本实例标识（主机名-pid 或配置值）
     */
    private String clusterInstanceId;

    /**
     * 主节点租约键
     */
    private String clusterLeaseKey;

    /**
     * 最近一次选主异常（正常为 null）
     */
    private String clusterLastError;

    @Schema(description = "每小时重启上限")
    private Integer restartLimitPerHour;

    @Schema(description = "是否已触发重启限流（FR-WD-05）")
    private Boolean restartLimitExceeded;

    @Schema(description = "看门狗日志尾部")
    private List<String> logTail = new ArrayList<>();
}
