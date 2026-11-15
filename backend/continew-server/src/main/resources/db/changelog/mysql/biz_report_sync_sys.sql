-- liquibase formatted sql

-- changeset jdrk:11
-- comment 上报目标表（FR-RPT-01~04：MQTT / 数据库 / HTTP 多目标并行）
CREATE TABLE IF NOT EXISTS `biz_report_target` (
    `id`                bigint        AUTO_INCREMENT              COMMENT 'ID',
    `name`              varchar(50)   NOT NULL                    COMMENT '目标名称',
    `target_type`       varchar(20)   NOT NULL DEFAULT 'MQTT'     COMMENT '目标类型（MQTT / DB / HTTP）',
    `db_type`           varchar(30)   DEFAULT NULL                COMMENT '数据库方言（MYSQL / MARIADB / POSTGRESQL / SQLSERVER / KINGBASE / ORACLE / DM）',
    `config_json`       varchar(2000) DEFAULT NULL                COMMENT '连接参数（JSON：host、port、topic、username、password、database 等）',
    `format_type`       varchar(20)   NOT NULL DEFAULT 'JSON'     COMMENT '报文格式（JSON / KV / CUSTOM）',
    `mqtt_qos`          tinyint       DEFAULT NULL                COMMENT 'MQTT QoS（0/1/2）',
    `mqtt_retain`       bit(1)        DEFAULT b'0'                COMMENT 'MQTT 保留标志',
    `retry_count`       int           NOT NULL DEFAULT 3          COMMENT '失败重试次数',
    `retry_interval_ms` int           NOT NULL DEFAULT 5000       COMMENT '重试间隔（毫秒）',
    `timeout_ms`        int           NOT NULL DEFAULT 10000      COMMENT '超时（毫秒）',
    `enabled`           bit(1)        NOT NULL DEFAULT b'1'       COMMENT '是否启用',
    `description`       varchar(200)  DEFAULT NULL                COMMENT '描述',
    `sort`              int           NOT NULL DEFAULT 999        COMMENT '排序',
    `create_user`       bigint        NOT NULL                    COMMENT '创建人',
    `create_time`       datetime      NOT NULL                    COMMENT '创建时间',
    `update_user`       bigint        DEFAULT NULL                COMMENT '修改人',
    `update_time`       datetime      DEFAULT NULL                COMMENT '修改时间',
    `deleted`           bigint        NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_name_deleted`(`name`, `deleted`),
    INDEX `idx_target_type`(`target_type`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上报目标表';

-- changeset jdrk:12
-- comment 上报模板表（FR-RPT-02：JSON / 键值对 / 自定义模板）
CREATE TABLE IF NOT EXISTS `biz_report_template` (
    `id`           bigint        AUTO_INCREMENT              COMMENT 'ID',
    `name`         varchar(50)   NOT NULL                    COMMENT '模板名称',
    `code`         varchar(50)   NOT NULL                    COMMENT '模板编码',
    `format_type`  varchar(20)   NOT NULL DEFAULT 'JSON'     COMMENT '报文格式（JSON / KV / CUSTOM）',
    `content`      text          DEFAULT NULL                COMMENT '模板内容，支持 ${} 变量占位',
    `is_system`    bit(1)        NOT NULL DEFAULT b'0'       COMMENT '是否系统内置模板',
    `enabled`      bit(1)        NOT NULL DEFAULT b'1'       COMMENT '是否启用',
    `description`  varchar(200)  DEFAULT NULL                COMMENT '描述',
    `create_user`  bigint        NOT NULL                    COMMENT '创建人',
    `create_time`  datetime      NOT NULL                    COMMENT '创建时间',
    `update_user`  bigint        DEFAULT NULL                COMMENT '修改人',
    `update_time`  datetime      DEFAULT NULL                COMMENT '修改时间',
    `deleted`      bigint        NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_code_deleted`(`code`, `deleted`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上报模板表';

-- changeset jdrk:13
-- comment 上报规则表（目标 + 模板 + 范围 + 频率）
CREATE TABLE IF NOT EXISTS `biz_report_rule` (
    `id`          bigint       AUTO_INCREMENT       COMMENT 'ID',
    `name`        varchar(50)  NOT NULL             COMMENT '规则名称',
    `target_id`   bigint       NOT NULL             COMMENT '上报目标 ID',
    `template_id` bigint       DEFAULT NULL         COMMENT '上报模板 ID',
    `port_id`     bigint       DEFAULT NULL         COMMENT '限定端口 ID（空表示全部）',
    `device_id`   bigint       DEFAULT NULL         COMMENT '限定设备 ID（空表示全部）',
    `interval_ms` int          NOT NULL DEFAULT 60000 COMMENT '上报间隔（毫秒，默认 60 秒）',
    `enabled`     bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `description` varchar(200) DEFAULT NULL         COMMENT '描述',
    `create_user` bigint       NOT NULL             COMMENT '创建人',
    `create_time` datetime     NOT NULL             COMMENT '创建时间',
    `update_user` bigint       DEFAULT NULL         COMMENT '修改人',
    `update_time` datetime     DEFAULT NULL         COMMENT '修改时间',
    `deleted`     bigint       NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_target_id`(`target_id`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上报规则表';

-- changeset jdrk:14
-- comment 同步任务表（FR-SYNC-01/04/06：全量与增量同步，支持暂停续传）
CREATE TABLE IF NOT EXISTS `biz_sync_task` (
    `id`              bigint        AUTO_INCREMENT              COMMENT 'ID',
    `name`            varchar(50)   NOT NULL                    COMMENT '任务名称',
    `target_id`       bigint        DEFAULT NULL                COMMENT '目标库（上报目标 ID）',
    `sync_mode`       varchar(20)   NOT NULL DEFAULT 'INCREMENT' COMMENT '同步模式（FULL：全量；INCREMENT：增量）',
    `conflict_policy` varchar(20)   NOT NULL DEFAULT 'NEWER_WINS' COMMENT '冲突策略（NEWER_WINS / OVERWRITE / SKIP / MANUAL）',
    `table_names`     varchar(500)  DEFAULT NULL                COMMENT '同步的表名（逗号分隔，对齐仁科 tbalarm/tbhistory/tbnoticelog/tbsyslog）',
    `task_status`     varchar(20)   NOT NULL DEFAULT 'IDLE'     COMMENT '状态（IDLE / RUNNING / PAUSED / DONE / FAILED）',
    `last_sync_time`  datetime      DEFAULT NULL                COMMENT '上次同步时间',
    `total_count`     bigint        NOT NULL DEFAULT 0          COMMENT '总记录数',
    `success_count`   bigint        NOT NULL DEFAULT 0          COMMENT '成功数',
    `fail_count`      bigint        NOT NULL DEFAULT 0          COMMENT '失败数',
    `error_msg`       varchar(1000) DEFAULT NULL                COMMENT '最近错误',
    `enabled`         bit(1)        NOT NULL DEFAULT b'1'       COMMENT '是否启用',
    `description`     varchar(200)  DEFAULT NULL                COMMENT '描述',
    `create_user`     bigint        NOT NULL                    COMMENT '创建人',
    `create_time`     datetime      NOT NULL                    COMMENT '创建时间',
    `update_user`     bigint        DEFAULT NULL                COMMENT '修改人',
    `update_time`     datetime      DEFAULT NULL                COMMENT '修改时间',
    `deleted`         bigint        NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_name_deleted`(`name`, `deleted`),
    INDEX `idx_task_status`(`task_status`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步任务表';

-- changeset jdrk:15
-- comment 同步冲突表（FR-SYNC-05：记录新旧冲突明细，可导出追溯）
CREATE TABLE IF NOT EXISTS `biz_sync_conflict` (
    `id`           bigint        AUTO_INCREMENT       COMMENT 'ID',
    `task_id`      bigint        NOT NULL             COMMENT '同步任务 ID',
    `table_name`   varchar(64)   NOT NULL             COMMENT '表名',
    `record_id`    varchar(64)   DEFAULT NULL         COMMENT '记录主键',
    `source_time`  datetime      DEFAULT NULL         COMMENT '来源记录时间',
    `target_time`  datetime      DEFAULT NULL         COMMENT '目标记录时间',
    `kept_source`  bit(1)        DEFAULT NULL         COMMENT '最终保留的是否为来源数据',
    `policy`       varchar(20)   DEFAULT NULL         COMMENT '应用的冲突策略',
    `detail`       varchar(1000) DEFAULT NULL         COMMENT '冲突详情',
    `create_user`  bigint        NOT NULL             COMMENT '创建人',
    `create_time`  datetime      NOT NULL             COMMENT '创建时间',
    `update_user`  bigint        DEFAULT NULL         COMMENT '修改人',
    `update_time`  datetime      DEFAULT NULL         COMMENT '修改时间',
    `deleted`      bigint        NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_task_id`(`task_id`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步冲突表';

-- changeset jdrk:16
-- comment 同步水位表（FR-SYNC-04：增量同步的时间水位与主键水位）
CREATE TABLE IF NOT EXISTS `biz_sync_watermark` (
    `id`               bigint       AUTO_INCREMENT       COMMENT 'ID',
    `target_id`        bigint       NOT NULL             COMMENT '目标库 ID',
    `table_name`       varchar(64)  NOT NULL             COMMENT '表名',
    `last_record_time` datetime     DEFAULT NULL         COMMENT '上次同步到的记录时间',
    `last_id`          varchar(64)  DEFAULT NULL         COMMENT '上次同步到的主键',
    `create_user`      bigint       NOT NULL             COMMENT '创建人',
    `create_time`      datetime     NOT NULL             COMMENT '创建时间',
    `update_user`      bigint       DEFAULT NULL         COMMENT '修改人',
    `update_time`      datetime     DEFAULT NULL         COMMENT '修改时间',
    `deleted`          bigint       NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_target_table_deleted`(`target_id`, `table_name`, `deleted`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步水位表';

-- changeset jdrk:17
-- comment 业务系统配置表（FR-CFG-01~04：采集/上报/存储/系统四类参数）
CREATE TABLE IF NOT EXISTS `biz_sys_config` (
    `id`            bigint       AUTO_INCREMENT       COMMENT 'ID',
    `category`      varchar(20)  NOT NULL             COMMENT '分类（COLLECT / REPORT / STORAGE / SYSTEM）',
    `name`          varchar(50)  NOT NULL             COMMENT '参数名称',
    `code`          varchar(50)  NOT NULL             COMMENT '参数编码',
    `value`         varchar(500) DEFAULT NULL         COMMENT '参数值',
    `default_value` varchar(500) DEFAULT NULL         COMMENT '默认值',
    `description`   varchar(200) DEFAULT NULL         COMMENT '说明',
    `create_user`   bigint       NOT NULL             COMMENT '创建人',
    `create_time`   datetime     NOT NULL             COMMENT '创建时间',
    `update_user`   bigint       DEFAULT NULL         COMMENT '修改人',
    `update_time`   datetime     DEFAULT NULL         COMMENT '修改时间',
    `deleted`       bigint       NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_code_deleted`(`code`, `deleted`),
    INDEX `idx_category`(`category`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务系统配置表';

-- changeset jdrk:18
-- comment 看门狗日志表（FR-WD-01~06：心跳超时、崩溃、重启与自检记录）
CREATE TABLE IF NOT EXISTS `biz_watchdog_log` (
    `id`            bigint       AUTO_INCREMENT       COMMENT 'ID',
    `process_name`  varchar(50)  NOT NULL             COMMENT '被监控进程名',
    `event_type`    varchar(30)  NOT NULL             COMMENT '事件类型（HEARTBEAT_TIMEOUT / CRASH / RESTART / SELF_CHECK / REBOOT_LIMIT）',
    `event_time`    datetime     NOT NULL             COMMENT '事件时间',
    `detail`        varchar(1000) DEFAULT NULL        COMMENT '事件详情',
    `restart_count` int          NOT NULL DEFAULT 0   COMMENT '累计重启次数',
    `create_user`   bigint       NOT NULL             COMMENT '创建人',
    `create_time`   datetime     NOT NULL             COMMENT '创建时间',
    `update_user`   bigint       DEFAULT NULL         COMMENT '修改人',
    `update_time`   datetime     DEFAULT NULL         COMMENT '修改时间',
    `deleted`       bigint       NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_event_time`(`event_time`),
    INDEX `idx_process_event`(`process_name`, `event_type`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看门狗日志表';
