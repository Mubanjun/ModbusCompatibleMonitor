-- liquibase formatted sql

-- changeset jdrk:6
-- comment 设备模板表（FR-DEV-12：内置常见仁科设备模板，支持自定义）
CREATE TABLE IF NOT EXISTS `biz_device_template` (
    `id`           bigint        AUTO_INCREMENT              COMMENT 'ID',
    `name`         varchar(50)   NOT NULL                    COMMENT '模板名称',
    `model`        varchar(50)   DEFAULT NULL                COMMENT '设备型号',
    `element_type` varchar(40)   DEFAULT NULL                COMMENT '水质要素类型（ElementType 枚举名）',
    `range_label`  varchar(100)  DEFAULT NULL                COMMENT '量程档位说明',
    `unit`         varchar(20)   DEFAULT NULL                COMMENT '单位',
    `data_type`    varchar(20)   NOT NULL DEFAULT 'ANALOG2_ONLY' COMMENT '通道数据属性',
    `encoding`     varchar(30)   NOT NULL DEFAULT 'UINT16_SCALE_1' COMMENT '值编码方式',
    `coefficient`  decimal(18,6) NOT NULL DEFAULT 1.000000   COMMENT '系数 A（工程值 = 原始值 × A + B）',
    `offset_value` decimal(18,6) NOT NULL DEFAULT 0.000000   COMMENT '偏移 B',
    `min_value`    decimal(18,6) DEFAULT NULL                COMMENT '量程下限',
    `max_value`    decimal(18,6) DEFAULT NULL                COMMENT '量程上限',
    `is_system`    bit(1)        NOT NULL DEFAULT b'0'       COMMENT '是否系统内置模板',
    `enabled`      bit(1)        NOT NULL DEFAULT b'1'       COMMENT '是否启用',
    `description`  varchar(200)  DEFAULT NULL                COMMENT '描述',
    `sort`         int           NOT NULL DEFAULT 999        COMMENT '排序',
    `create_user`  bigint        NOT NULL                    COMMENT '创建人',
    `create_time`  datetime      NOT NULL                    COMMENT '创建时间',
    `update_user`  bigint        DEFAULT NULL                COMMENT '修改人',
    `update_time`  datetime      DEFAULT NULL                COMMENT '修改时间',
    `deleted`      bigint        NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_name_deleted`(`name`, `deleted`),
    INDEX `idx_element_type`(`element_type`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备模板表';

-- changeset jdrk:7
-- comment 采集顺序表（FR-ACQ-04：端口内设备的轮询顺序，顺序持久化）
CREATE TABLE IF NOT EXISTS `biz_collect_order` (
    `id`          bigint   AUTO_INCREMENT       COMMENT 'ID',
    `port_id`     bigint   NOT NULL             COMMENT '所属端口 ID',
    `device_id`   bigint   NOT NULL             COMMENT '设备 ID',
    `sort_no`     int      NOT NULL DEFAULT 999 COMMENT '采集顺序',
    `enabled`     bit(1)   NOT NULL DEFAULT b'1' COMMENT '是否参与轮询',
    `create_user` bigint   NOT NULL             COMMENT '创建人',
    `create_time` datetime NOT NULL             COMMENT '创建时间',
    `update_user` bigint   DEFAULT NULL         COMMENT '修改人',
    `update_time` datetime DEFAULT NULL         COMMENT '修改时间',
    `deleted`     bigint   NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_port_device_deleted`(`port_id`, `device_id`, `deleted`),
    INDEX `idx_port_sort`(`port_id`, `sort_no`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集顺序表';

-- changeset jdrk:8
-- comment 报警规则表（FR-ALM-01/05/06：阈值、偏差、变化率与开关量报警，含抑制与防抖）
CREATE TABLE IF NOT EXISTS `biz_alarm_rule` (
    `id`                bigint        AUTO_INCREMENT              COMMENT 'ID',
    `name`              varchar(50)   NOT NULL                    COMMENT '规则名称',
    `device_id`         bigint        DEFAULT NULL                COMMENT '设备 ID（空表示按端口/全局）',
    `port_id`           bigint        DEFAULT NULL                COMMENT '端口 ID',
    `channel_no`        int           DEFAULT NULL                COMMENT '通道号（1~32）',
    `analog_no`         int           DEFAULT NULL                COMMENT '模拟量序号（1 或 2）',
    `element_type`      varchar(40)   DEFAULT NULL                COMMENT '要素类型',
    `rule_type`         varchar(20)   NOT NULL DEFAULT 'RANGE'    COMMENT '规则类型（RANGE：阈值；DEVIATION：偏差；RATE：变化率；SWITCH：开关量）',
    `upper_limit`       decimal(18,6) DEFAULT NULL                COMMENT '上限',
    `lower_limit`       decimal(18,6) DEFAULT NULL                COMMENT '下限',
    `deviation`         decimal(18,6) DEFAULT NULL                COMMENT '允许偏差',
    `rate_limit`        decimal(18,6) DEFAULT NULL                COMMENT '变化率上限（单位/分钟）',
    `switch_state`      tinyint       DEFAULT NULL                COMMENT '开关量报警状态（0：断开；1：闭合）',
    `duration_s`        int           NOT NULL DEFAULT 0          COMMENT '持续超过多久才报警（秒，防抖）',
    `suppress_window_s` int           NOT NULL DEFAULT 0          COMMENT '抑制窗口（秒，窗口内同类报警不重复产生）',
    `notify_methods`    varchar(100)  DEFAULT NULL                COMMENT '通知方式（逗号分隔：MQTT,EMAIL,SMS）',
    `enabled`           bit(1)        NOT NULL DEFAULT b'1'       COMMENT '是否启用',
    `description`       varchar(200)  DEFAULT NULL                COMMENT '描述',
    `create_user`       bigint        NOT NULL                    COMMENT '创建人',
    `create_time`       datetime      NOT NULL                    COMMENT '创建时间',
    `update_user`       bigint        DEFAULT NULL                COMMENT '修改人',
    `update_time`       datetime      DEFAULT NULL                COMMENT '修改时间',
    `deleted`           bigint        NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_name_deleted`(`name`, `deleted`),
    INDEX `idx_device_channel`(`device_id`, `channel_no`, `analog_no`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警规则表';

-- changeset jdrk:9
-- comment 报警记录表（FR-ALM-02：字段与仁科 tbalarm 语义对齐，便于后续同步映射）
CREATE TABLE IF NOT EXISTS `biz_alarm_record` (
    `id`               bigint        AUTO_INCREMENT              COMMENT 'ID',
    `rule_id`          bigint        DEFAULT NULL                COMMENT '命中的规则 ID',
    `device_id`        bigint        DEFAULT NULL                COMMENT '设备 ID',
    `device_name`      varchar(255)  DEFAULT NULL                COMMENT '设备名称（对应 tbalarm.DeviceName）',
    `device_addr`      int           DEFAULT NULL                COMMENT '设备地址（对应 tbalarm.DeviceAddr）',
    `node_id`          int           DEFAULT NULL                COMMENT '节点编号（对应 tbalarm.NodeId，即通道号）',
    `analog_no`        int           DEFAULT NULL                COMMENT '模拟量序号（1 或 2）',
    `element_name`     varchar(50)   DEFAULT NULL                COMMENT '要素名称',
    `alarm_type`       varchar(255)  DEFAULT NULL                COMMENT '报警类型（对应 tbalarm.AlarmType）',
    `alarm_message`    varchar(255)  DEFAULT NULL                COMMENT '报警信息（对应 tbalarm.AlarmMessage）',
    `data_no`          int           DEFAULT NULL                COMMENT '数据序号（对应 tbalarm.DataNo）',
    `data_value`       double(20,4)  DEFAULT NULL                COMMENT '报警数值（对应 tbalarm.DataValue）',
    `unit`             varchar(20)   DEFAULT NULL                COMMENT '单位',
    `alarm_range`      varchar(255)  DEFAULT NULL                COMMENT '报警范围（对应 tbalarm.AlarmRange）',
    `record_time`      datetime      NOT NULL                    COMMENT '记录时间（对应 tbalarm.RecordTime）',
    `handling_flag`    tinyint       NOT NULL DEFAULT 0          COMMENT '处理标志（0：未处理；1：已处理；2：已忽略）',
    `handling_method`  varchar(2000) DEFAULT NULL                COMMENT '处理方式（对应 tbalarm.HandlingMethod）',
    `handling_user`    varchar(255)  DEFAULT NULL                COMMENT '处理人（对应 tbalarm.HandlingUser）',
    `handling_time`    datetime      DEFAULT NULL                COMMENT '处理时间（对应 tbalarm.HandlingTime）',
    `lng`              float(11,6)   DEFAULT NULL                COMMENT '经度（对应 tbalarm.Lng）',
    `lat`              float(11,6)   DEFAULT NULL                COMMENT '纬度（对应 tbalarm.Lat）',
    `create_user`      bigint        NOT NULL                    COMMENT '创建人',
    `create_time`      datetime      NOT NULL                    COMMENT '创建时间',
    `update_user`      bigint        DEFAULT NULL                COMMENT '修改人',
    `update_time`      datetime      DEFAULT NULL                COMMENT '修改时间',
    `deleted`          bigint        NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_record_time`(`record_time`),
    INDEX `idx_device_addr`(`device_addr`),
    INDEX `idx_node_id`(`node_id`),
    INDEX `idx_handling_flag`(`handling_flag`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警记录表';

-- changeset jdrk:10
-- comment 报警通知表（FR-ALM-04：通知方式、次数与结果）
CREATE TABLE IF NOT EXISTS `biz_alarm_notify` (
    `id`           bigint        AUTO_INCREMENT       COMMENT 'ID',
    `record_id`    bigint        NOT NULL             COMMENT '报警记录 ID',
    `notify_type`  varchar(20)   NOT NULL             COMMENT '通知方式（MQTT / EMAIL / SMS）',
    `target`       varchar(255)  DEFAULT NULL         COMMENT '通知目标（主题/邮箱/手机号）',
    `content`      varchar(2000) DEFAULT NULL         COMMENT '通知内容',
    `send_status`  tinyint       NOT NULL DEFAULT 0   COMMENT '发送状态（0：待发送；1：成功；2：失败）',
    `retry_count`  int           NOT NULL DEFAULT 0   COMMENT '重试次数',
    `send_time`    datetime      DEFAULT NULL         COMMENT '发送时间',
    `error_msg`    varchar(500)  DEFAULT NULL         COMMENT '失败原因',
    `create_user`  bigint        NOT NULL             COMMENT '创建人',
    `create_time`  datetime      NOT NULL             COMMENT '创建时间',
    `update_user`  bigint        DEFAULT NULL         COMMENT '修改人',
    `update_time`  datetime      DEFAULT NULL         COMMENT '修改时间',
    `deleted`      bigint        NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_record_id`(`record_id`),
    INDEX `idx_send_status`(`send_status`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警通知表';
