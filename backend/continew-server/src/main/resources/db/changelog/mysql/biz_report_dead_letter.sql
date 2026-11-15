-- liquibase formatted sql

-- changeset jdrk:47
-- comment 上报死信队列表（FR-RPT-05：超限失败进入死信，可人工重放）
CREATE TABLE IF NOT EXISTS `biz_report_dead_letter` (
    `id`                bigint        AUTO_INCREMENT       COMMENT 'ID',
    `target_id`         bigint        DEFAULT NULL         COMMENT '上报目标 ID',
    `rule_id`           bigint        DEFAULT NULL         COMMENT '上报规则 ID',
    `target_name`       varchar(50)   DEFAULT NULL         COMMENT '目标名称',
    `rule_name`         varchar(50)   DEFAULT NULL         COMMENT '规则名称',
    `target_type`       varchar(20)   DEFAULT NULL         COMMENT '目标类型（MQTT / DB / HTTP）',
    `format_type`       varchar(20)   DEFAULT NULL         COMMENT '报文格式（JSON / KV / CUSTOM）',
    `payload`           text          DEFAULT NULL         COMMENT '失败时的报文内容',
    `item_count`        int           NOT NULL DEFAULT 0   COMMENT '报文包含的数据条数',
    `retry_count`       int           NOT NULL DEFAULT 0   COMMENT '已重试次数',
    `state`             varchar(20)   NOT NULL DEFAULT 'DEAD' COMMENT '状态（DEAD：死信；REPLAYED：已重放成功；REPLAY_FAILED：重放失败）',
    `error_msg`         varchar(1000) DEFAULT NULL         COMMENT '失败原因',
    `first_fail_time`   datetime      DEFAULT NULL         COMMENT '首次失败时间',
    `replay_time`       datetime      DEFAULT NULL         COMMENT '最近重放时间',
    `replay_result`     varchar(500)  DEFAULT NULL         COMMENT '重放结果说明',
    `create_user`       bigint        NOT NULL DEFAULT 0   COMMENT '创建人（运行时写入固定为 0：系统）',
    `create_time`       datetime      NOT NULL             COMMENT '创建时间',
    `update_user`       bigint        DEFAULT NULL         COMMENT '修改人',
    `update_time`       datetime      DEFAULT NULL         COMMENT '修改时间',
    `deleted`           bigint        NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_dl_state`(`state`),
    INDEX `idx_dl_target`(`target_id`),
    INDEX `idx_dl_create`(`create_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上报死信队列表';

-- changeset jdrk:48
-- comment 新增上报死信菜单与权限（FR-RPT-05）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4200, '上报死信', 4000, 2, '/env/report-dead-letter', 'EnvReportDeadLetter', 'env/report-dead-letter/index', NULL, 'exclamation-circle', b'0', b'0', b'0', NULL, 21, 1, 1, NOW()),
(4201, '列表', 4200, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:deadLetter:list', 1, 1, 1, NOW()),
(4202, '详情', 4200, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:deadLetter:get', 2, 1, 1, NOW()),
(4203, '重放', 4200, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:deadLetter:replay', 3, 1, 1, NOW());

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4200), (2, 4201), (2, 4202), (2, 4203),
(547888897925840927, 4200), (547888897925840927, 4201), (547888897925840927, 4202), (547888897925840927, 4203);
