-- liquibase formatted sql

-- changeset jdrk:51
-- comment 设备补充继电器配置（FR-RLY-01：标准 Modbus 规约 696~703 为 1~8 号继电器）
ALTER TABLE `biz_device`
    ADD COLUMN `relay_reg_base` int NOT NULL DEFAULT 696 COMMENT '继电器寄存器起始地址（标准规约 696）' AFTER `priority`;

ALTER TABLE `biz_device`
    ADD COLUMN `relay_count` int NOT NULL DEFAULT 8 COMMENT '继电器路数（标准规约 8 路）' AFTER `relay_reg_base`;

-- changeset jdrk:52
-- comment 继电器操作审计表（FR-RLY-04：记录操作人、设备、继电器、动作、时间、结果）
CREATE TABLE IF NOT EXISTS `biz_relay_log` (
    `id`             bigint        AUTO_INCREMENT       COMMENT 'ID',
    `device_id`      bigint        DEFAULT NULL         COMMENT '设备 ID',
    `device_name`    varchar(255)  DEFAULT NULL         COMMENT '设备名称',
    `device_addr`    int           DEFAULT NULL         COMMENT '设备地址（从站地址）',
    `port_id`        bigint        DEFAULT NULL         COMMENT '端口 ID',
    `relay_no`       int           DEFAULT NULL         COMMENT '继电器序号（1~8），批量操作时为空',
    `action`         varchar(20)   NOT NULL             COMMENT '动作（CLOSE：闭合；OPEN：断开；BATCH：批量；AUTO_RESTORE：定时恢复）',
    `request_value`  varchar(100)  DEFAULT NULL         COMMENT '请求值（单路为 0/1，批量逗号分隔）',
    `previous_value` varchar(100)  DEFAULT NULL         COMMENT '操作前状态',
    `readback_value` varchar(100)  DEFAULT NULL         COMMENT '回读状态（FR-RLY-02 操作后状态回读一致）',
    `readback_ok`    bit(1)        NOT NULL DEFAULT b'0' COMMENT '回读是否与请求一致',
    `duration_ms`    int           NOT NULL DEFAULT 0   COMMENT '执行时长（毫秒，0 表示保持）',
    `result_status`  varchar(20)   NOT NULL             COMMENT '结果（SUCCESS：成功；FAILED：失败；REJECTED：被拒绝）',
    `error_msg`      varchar(500)  DEFAULT NULL         COMMENT '失败原因',
    `operate_time`   datetime      NOT NULL             COMMENT '操作时间',
    `operator_id`    bigint        DEFAULT NULL         COMMENT '操作人 ID（系统自动恢复为空）',
    `operator_name`  varchar(255)  DEFAULT NULL         COMMENT '操作人名称',
    `create_user`    bigint        NOT NULL DEFAULT 0   COMMENT '创建人',
    `create_time`    datetime      NOT NULL             COMMENT '创建时间',
    `update_user`    bigint        DEFAULT NULL         COMMENT '修改人',
    `update_time`    datetime      DEFAULT NULL         COMMENT '修改时间',
    `deleted`        bigint        NOT NULL DEFAULT 0   COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_relay_device`(`device_id`, `operate_time`),
    INDEX `idx_relay_time`(`operate_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='继电器操作审计表';

-- changeset jdrk:53
-- comment 继电器写开关系统配置（FR-PROTO-09 写保护）
INSERT INTO `biz_sys_config`
(`category`, `name`, `code`, `value`, `default_value`, `description`, `create_user`, `create_time`)
VALUES
('SYSTEM', '允许继电器写入', 'RELAY_WRITE_ENABLED', '1', '1', '是否允许通过平台下发继电器控制（0：禁止写入，FR-PROTO-09）', 1, NOW()),
('SYSTEM', '继电器最大执行时长', 'RELAY_MAX_DURATION_MS', '600000', '600000', '单次继电器定时恢复的最大时长（毫秒）', 1, NOW());

-- changeset jdrk:54
-- comment 新增继电器控制菜单与权限（FR-RLY-01~04）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4220, '继电器控制', 4000, 2, '/env/relay', 'EnvRelay', 'env/relay/index', NULL, 'poweroff', b'0', b'0', b'0', NULL, 22, 1, 1, NOW()),
(4221, '状态查询', 4220, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:relay:status', 1, 1, 1, NOW()),
(4222, '继电器操作', 4220, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:relay:control', 2, 1, 1, NOW()),
(4223, '操作日志', 4220, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:relayLog:list', 3, 1, 1, NOW());

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4220), (2, 4221), (2, 4222), (2, 4223),
(547888897925840927, 4220), (547888897925840927, 4221), (547888897925840927, 4222), (547888897925840927, 4223);
