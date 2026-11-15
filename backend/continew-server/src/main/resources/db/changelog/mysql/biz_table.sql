-- liquibase formatted sql

-- changeset jdrk:1
-- comment 采集端口配置表
CREATE TABLE IF NOT EXISTS `biz_port` (
    `id`           bigint       AUTO_INCREMENT              COMMENT 'ID',
    `name`         varchar(50)  NOT NULL                    COMMENT '端口名称',
    `port_type`    varchar(20)  NOT NULL DEFAULT 'SERIAL'   COMMENT '端口类型（SERIAL：串口；TCP：串口服务器/Modbus TCP）',
    `port_name`    varchar(100) NOT NULL                    COMMENT '端口地址（串口号如 COM1，或 IP:端口）',
    `baud_rate`    int          DEFAULT 4800                COMMENT '波特率（TCP 端口可空，仁科设备默认 4800）',
    `data_bits`    tinyint      NOT NULL DEFAULT 8          COMMENT '数据位',
    `stop_bits`    tinyint      NOT NULL DEFAULT 1          COMMENT '停止位',
    `parity`       tinyint      NOT NULL DEFAULT 0          COMMENT '校验位（0：无；1：奇校验；2：偶校验）',
    `flow_control` tinyint      NOT NULL DEFAULT 0          COMMENT '流控（0：无；1：RTS/CTS；2：XON/XOFF）',
    `device_limit` int          NOT NULL DEFAULT 32         COMMENT '本端口 485 接入设备数量上限（默认 32，可配 128/256）',
    `enabled`      bit(1)       NOT NULL DEFAULT b'1'       COMMENT '是否启用',
    `description`  varchar(200) DEFAULT NULL                COMMENT '描述',
    `sort`         int          NOT NULL DEFAULT 999        COMMENT '排序',
    `create_user`  bigint       NOT NULL                    COMMENT '创建人',
    `create_time`  datetime     NOT NULL                    COMMENT '创建时间',
    `update_user`  bigint       DEFAULT NULL                COMMENT '修改人',
    `update_time`  datetime     DEFAULT NULL                COMMENT '修改时间',
    `deleted`      bigint       NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_port_type_name_deleted`(`port_type`, `port_name`, `deleted`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集端口配置表';

-- changeset jdrk:2
-- comment 采集设备配置表
CREATE TABLE IF NOT EXISTS `biz_device` (
    `id`          bigint        AUTO_INCREMENT              COMMENT 'ID',
    `port_id`     bigint        NOT NULL                    COMMENT '所属端口 ID',
    `name`        varchar(50)   NOT NULL                    COMMENT '设备名称',
    `slave_addr`  int           NOT NULL                    COMMENT '从站地址（1~255，同一端口内唯一）',
    `protocol`    varchar(20)   NOT NULL DEFAULT 'RS_MODBUS' COMMENT '协议（MODBUS_RTU / RS_MODBUS / MODBUS_TCP）',
    `model`       varchar(50)   DEFAULT NULL                COMMENT '设备型号',
    `group_name`  varchar(50)   DEFAULT NULL                COMMENT '分组',
    `location`    varchar(200)  DEFAULT NULL                COMMENT '安装位置',
    `longitude`   decimal(10,6) DEFAULT NULL                COMMENT '经度',
    `latitude`    decimal(10,6) DEFAULT NULL                COMMENT '纬度',
    `interval_ms` int           DEFAULT NULL                COMMENT '设备级采集间隔（毫秒，空则继承端口/全局）',
    `timeout_ms`  int           NOT NULL DEFAULT 1000       COMMENT '单帧超时（毫秒）',
    `sort_no`     int           NOT NULL DEFAULT 999        COMMENT '端口内采集顺序',
    `enabled`     bit(1)        NOT NULL DEFAULT b'1'       COMMENT '是否启用',
    `description` varchar(200)  DEFAULT NULL                COMMENT '描述',
    `create_user` bigint        NOT NULL                    COMMENT '创建人',
    `create_time` datetime      NOT NULL                    COMMENT '创建时间',
    `update_user` bigint        DEFAULT NULL                COMMENT '修改人',
    `update_time` datetime      DEFAULT NULL                COMMENT '修改时间',
    `deleted`     bigint        NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_port_slave_addr_deleted`(`port_id`, `slave_addr`, `deleted`),
    INDEX `idx_port_id`(`port_id`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集设备配置表';

-- changeset jdrk:3
-- comment 通道与模拟量配置表
CREATE TABLE IF NOT EXISTS `biz_channel` (
    `id`           bigint         AUTO_INCREMENT              COMMENT 'ID',
    `device_id`    bigint         NOT NULL                    COMMENT '所属设备 ID',
    `channel_no`   int            NOT NULL                    COMMENT '通道号（1~32）',
    `analog_no`    int            NOT NULL                    COMMENT '模拟量序号（1 或 2）',
    `element_type` varchar(40)    DEFAULT NULL                COMMENT '水质要素类型（ElementType 枚举名）',
    `element_name` varchar(50)    DEFAULT NULL                COMMENT '要素显示名称',
    `unit`         varchar(20)    DEFAULT NULL                COMMENT '单位',
    `data_type`    varchar(20)    NOT NULL DEFAULT 'ANALOG2_ONLY' COMMENT '通道数据属性（ANALOG1_ONLY / ANALOG2_ONLY / BOTH / FLOAT32）',
    `encoding`     varchar(30)    NOT NULL DEFAULT 'UINT16_SCALE_1' COMMENT '值编码方式（ValueEncoding 枚举名）',
    `reg_addr`     int            NOT NULL DEFAULT 0          COMMENT '寄存器起始地址',
    `coefficient`  decimal(18,6)  NOT NULL DEFAULT 1.000000   COMMENT '系数 A（工程值 = 原始值 × A + B）',
    `offset_value` decimal(18,6)  NOT NULL DEFAULT 0.000000   COMMENT '偏移 B',
    `min_value`    decimal(18,6)  DEFAULT NULL                COMMENT '量程下限',
    `max_value`    decimal(18,6)  DEFAULT NULL                COMMENT '量程上限',
    `enabled`      bit(1)         NOT NULL DEFAULT b'1'       COMMENT '是否启用',
    `description`  varchar(200)   DEFAULT NULL                COMMENT '描述',
    `create_user`  bigint         NOT NULL                    COMMENT '创建人',
    `create_time`  datetime       NOT NULL                    COMMENT '创建时间',
    `update_user`  bigint         DEFAULT NULL                COMMENT '修改人',
    `update_time`  datetime       DEFAULT NULL                COMMENT '修改时间',
    `deleted`      bigint         NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_device_channel_analog_deleted`(`device_id`, `channel_no`, `analog_no`, `deleted`),
    INDEX `idx_device_id`(`device_id`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通道与模拟量配置表';
