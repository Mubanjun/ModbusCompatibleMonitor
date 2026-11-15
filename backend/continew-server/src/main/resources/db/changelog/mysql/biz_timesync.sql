-- liquibase formatted sql

-- changeset jdrk:65
-- comment 设备校时系统配置（NFR-REL-06、FR-PROTO-09 写保护）
INSERT INTO `biz_sys_config`
(`category`, `name`, `code`, `value`, `default_value`, `description`, `create_user`, `create_time`)
VALUES
('SYSTEM', '允许校时写入', 'TIME_SYNC_WRITE_ENABLED', '1', '1', '是否允许向设备下发时间（0：禁止写入，FR-PROTO-09）', 1, NOW()),
('SYSTEM', '校时偏差容差', 'TIME_SYNC_TOLERANCE_SECONDS', '5', '5', '设备时间与平台时间的偏差不超过该秒数时视为准确，自动校时跳过写入（NFR-REL-06）', 1, NOW()),
('SYSTEM', '时间寄存器起始地址', 'TIME_SYNC_REG_BASE', '684', '684', '标准 ModBus 规约的时间寄存器起始地址（684 = 年，附录 A）', 1, NOW()),
('SYSTEM', '启用自动定时校时', 'TIME_SYNC_AUTO_ENABLED', '0', '0', '是否启用自动定时校时（0：关闭，仅手动一键校时；计划见 jdrk.time-sync.auto-cron）', 1, NOW());

-- changeset jdrk:66
-- comment 新增设备校时菜单与权限（NFR-REL-06）
-- 注意：菜单 id 4200~4274 已被环境监测各页面占用，这里从 4280 起分配，避免主键冲突导致变更集失败
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4280, '设备校时', 4000, 2, '/env/time-sync', 'EnvTimeSync', 'env/time-sync/index', NULL, 'clock-circle', b'0', b'0', b'0', NULL, 26, 1, 1, NOW()),
(4281, '偏差检测', 4280, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:timeSync:status', 1, 1, 1, NOW()),
(4282, '一键校时', 4280, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:timeSync:sync', 2, 1, 1, NOW());

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4280), (2, 4281), (2, 4282),
(547888897925840927, 4280), (547888897925840927, 4281), (547888897925840927, 4282);
