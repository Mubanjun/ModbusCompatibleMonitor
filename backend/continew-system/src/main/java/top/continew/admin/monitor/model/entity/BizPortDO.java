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
 * 采集端口实体
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_port")
public class BizPortDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 端口名称
     */
    private String name;

    /**
     * 端口类型（SERIAL：串口；TCP：串口服务器/Modbus TCP）
     */
    private String portType;

    /**
     * 端口地址（串口号如 COM1，或 IP:端口）
     */
    private String portName;

    /**
     * 波特率
     */
    private Integer baudRate;

    /**
     * 数据位
     */
    private Integer dataBits;

    /**
     * 停止位
     */
    private Integer stopBits;

    /**
     * 校验位（0：无；1：奇校验；2：偶校验）
     */
    private Integer parity;

    /**
     * 流控（0：无；1：RTS/CTS；2：XON/XOFF）
     */
    private Integer flowControl;

    /**
     * 端口级采集间隔（毫秒，空则继承全局）
     */
    private Integer intervalMs;

    /**
     * 本端口 485 接入设备数量上限
     */
    private Integer deviceLimit;

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
