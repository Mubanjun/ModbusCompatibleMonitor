-- liquibase formatted sql

-- changeset jdrk:35
-- comment 补充三级采集间隔的端口级参数（FR-ACQ-01）与设备优先级（FR-ACQ-11）
ALTER TABLE `biz_port`
    ADD COLUMN `interval_ms` int DEFAULT NULL COMMENT '端口级采集间隔（毫秒，空则继承全局）' AFTER `flow_control`;

ALTER TABLE `biz_device`
    ADD COLUMN `priority` tinyint NOT NULL DEFAULT 0 COMMENT '高优先级（0：普通；1：高优先级，优先调度）' AFTER `sort_no`;

-- changeset jdrk:36
-- comment 信令记录表（FR-SIG-01/02/03：实时信令流、过滤检索与滚动留存）
CREATE TABLE IF NOT EXISTS `biz_signal_log` (
    `id`           bigint        AUTO_INCREMENT       COMMENT 'ID',
    `port_id`      bigint        DEFAULT NULL         COMMENT '端口 ID',
    `port_name`    varchar(100)  DEFAULT NULL         COMMENT '端口地址',
    `device_id`    bigint        DEFAULT NULL         COMMENT '设备 ID',
    `device_addr`  int           DEFAULT NULL         COMMENT '从站地址',
    `direction`    varchar(10)   NOT NULL             COMMENT '方向（OUT：发送；IN：接收；ERROR：异常）',
    `frame`        varchar(1024) DEFAULT NULL         COMMENT '十六进制报文',
    `frame_length` int           NOT NULL DEFAULT 0   COMMENT '报文字节数',
    `parsed`       varchar(500)  DEFAULT NULL         COMMENT '解析结果',
    `duration_ms`  int           DEFAULT NULL         COMMENT '耗时（毫秒）',
    `success`      bit(1)        NOT NULL DEFAULT b'1' COMMENT '是否成功',
    `error_msg`    varchar(500)  DEFAULT NULL         COMMENT '失败原因',
    `trace_time`   datetime(3)   NOT NULL             COMMENT '报文时间（毫秒精度）',
    `create_user`  bigint        NOT NULL DEFAULT 0   COMMENT '创建人（运行时写入固定为 0：系统）',
    `create_time`  datetime      NOT NULL             COMMENT '创建时间',
    `update_user`  bigint        DEFAULT NULL         COMMENT '修改人',
    `update_time`  datetime      DEFAULT NULL         COMMENT '修改时间',
    `deleted`      bigint        NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_trace_time`(`trace_time`),
    INDEX `idx_device_time`(`device_id`, `trace_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信令记录表';

-- changeset jdrk:37
-- comment 采集历史数据表（FR-WEB-05/DR-02，字段语义对齐仁科 tbhistory，便于同步与第三方取数）
CREATE TABLE IF NOT EXISTS `biz_data` (
    `id`           bigint        AUTO_INCREMENT       COMMENT 'ID',
    `device_id`    bigint        DEFAULT NULL         COMMENT '设备 ID',
    `device_name`  varchar(255)  DEFAULT NULL         COMMENT '设备名称（对应 tbhistory.DeviceName）',
    `device_addr`  int           DEFAULT NULL         COMMENT '设备地址（对应 tbhistory.DeviceAddr）',
    `node_id`      int           DEFAULT NULL         COMMENT '节点编号（对应 tbhistory.NodeId，即通道号）',
    `analog_no`    int           DEFAULT NULL         COMMENT '模拟量序号（1 或 2）',
    `element_type` varchar(40)   DEFAULT NULL         COMMENT '要素类型',
    `element_name` varchar(50)   DEFAULT NULL         COMMENT '要素名称',
    `data_value`   double(20,4)  DEFAULT NULL         COMMENT '工程值（对应 tbhistory.DataValue）',
    `raw_value`    double(20,4)  DEFAULT NULL         COMMENT '原始值（未乘系数 A/B）',
    `unit`         varchar(20)   DEFAULT NULL         COMMENT '单位',
    `alarm_flag`   tinyint       NOT NULL DEFAULT 0   COMMENT '是否报警（0：否；1：是）',
    `record_time`  datetime      NOT NULL             COMMENT '记录时间（对应 tbhistory.RecordTime）',
    `create_user`  bigint        NOT NULL DEFAULT 0   COMMENT '创建人（运行时写入固定为 0：系统）',
    `create_time`  datetime      NOT NULL             COMMENT '创建时间',
    `update_user`  bigint        DEFAULT NULL         COMMENT '修改人',
    `update_time`  datetime      DEFAULT NULL         COMMENT '修改时间',
    `deleted`      bigint        NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_record_time`(`record_time`),
    INDEX `idx_device_time`(`device_id`, `record_time`),
    INDEX `idx_node`(`device_id`, `node_id`, `analog_no`, `record_time`),
    INDEX `idx_alarm_flag`(`alarm_flag`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集历史数据表';

-- changeset jdrk:38
-- comment 通道实时值表（FR-WEB-01/03：每设备每通道每模拟量保留一行最新值，含状态与质量）
CREATE TABLE IF NOT EXISTS `biz_data_latest` (
    `id`            bigint        AUTO_INCREMENT       COMMENT 'ID',
    `device_id`     bigint        NOT NULL             COMMENT '设备 ID',
    `device_name`   varchar(255)  DEFAULT NULL         COMMENT '设备名称',
    `device_addr`   int           DEFAULT NULL         COMMENT '设备地址（从站地址）',
    `port_id`       bigint        DEFAULT NULL         COMMENT '端口 ID',
    `node_id`       int           NOT NULL             COMMENT '节点编号（通道号）',
    `analog_no`     int           NOT NULL             COMMENT '模拟量序号（1 或 2）',
    `element_type`  varchar(40)   DEFAULT NULL         COMMENT '要素类型',
    `element_name`  varchar(50)   DEFAULT NULL         COMMENT '要素名称',
    `data_value`    double(20,4)  DEFAULT NULL         COMMENT '工程值',
    `raw_value`     double(20,4)  DEFAULT NULL         COMMENT '原始值',
    `unit`          varchar(20)   DEFAULT NULL         COMMENT '单位',
    `device_status` varchar(20)   NOT NULL DEFAULT 'UNKNOWN' COMMENT '设备状态（ONLINE / OFFLINE / DISABLED / ALARM / COMM_ERROR）',
    `quality`       varchar(20)   NOT NULL DEFAULT 'GOOD' COMMENT '数据质量（GOOD / BAD / STALE）',
    `over_range`    bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否超量程',
    `collect_time`  datetime(3)   NOT NULL             COMMENT '采集时间（毫秒精度）',
    `create_user`   bigint        NOT NULL DEFAULT 0   COMMENT '创建人（运行时写入固定为 0：系统）',
    `create_time`   datetime      NOT NULL             COMMENT '创建时间',
    `update_user`   bigint        DEFAULT NULL         COMMENT '修改人',
    `update_time`   datetime      DEFAULT NULL         COMMENT '修改时间',
    `deleted`       bigint        NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_device_node_analog`(`device_id`, `node_id`, `analog_no`),
    INDEX `idx_collect_time`(`collect_time`),
    INDEX `idx_device_status`(`device_status`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通道实时值表';
