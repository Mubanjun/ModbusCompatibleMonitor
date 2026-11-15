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
import java.math.BigDecimal;

/**
 * 采集设备实体
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_device")
public class BizDeviceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属端口 ID
     */
    private Long portId;

    /**
     * 设备名称
     */
    private String name;

    /**
     * 从站地址（1~255，同一端口内唯一）
     */
    private Integer slaveAddr;

    /**
     * 协议（MODBUS_RTU / RS_MODBUS / MODBUS_TCP）
     */
    private String protocol;

    /**
     * 设备型号
     */
    private String model;

    /**
     * 所属分组 ID
     */
    private Long groupId;

    /**
     * 分组（兼容只读）
     */
    private String groupName;

    /**
     * 安装位置
     */
    private String location;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    /**
     * 海拔（米）
     */
    private BigDecimal elevation;

    /**
     * 地址
     */
    private String address;

    /**
     * 设备级采集间隔（毫秒，空则继承端口/全局）
     */
    private Integer intervalMs;

    /**
     * 单帧超时（毫秒）
     */
    private Integer timeoutMs;

    /**
     * 端口内采集顺序
     */
    private Integer sortNo;

    /**
     * 是否高优先级（0：普通；1：高优先级，优先调度）
     */
    private Integer priority;

    /**
     * 继电器寄存器起始地址（标准规约 696）
     */
    private Integer relayRegBase;

    /**
     * 继电器路数（标准规约 8 路）
     */
    private Integer relayCount;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 描述
     */
    private String description;
}
