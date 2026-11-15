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

/**
 * 上报目标实体
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_report_target")
public class BizReportTargetDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标名称
     */
    private String name;

    /**
     * 目标类型（MQTT / DB / HTTP）
     */
    private String targetType;

    /**
     * 数据库方言（MYSQL / MARIADB / POSTGRESQL / SQLSERVER / KINGBASE / ORACLE / DM）
     */
    private String dbType;

    /**
     * 连接参数（JSON）
     */
    private String configJson;

    /**
     * 报文格式（JSON / KV / CUSTOM）
     */
    private String formatType;

    /**
     * MQTT QoS（0/1/2）
     */
    private Integer mqttQos;

    /**
     * MQTT 保留标志
     */
    private Boolean mqttRetain;

    /**
     * 失败重试次数
     */
    private Integer retryCount;

    /**
     * 重试间隔（毫秒）
     */
    private Integer retryIntervalMs;

    /**
     * 超时（毫秒）
     */
    private Integer timeoutMs;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 描述
     */
    private String description;

    /**
     * 排序
     */
    private Integer sort;
}
