-- liquibase formatted sql

-- changeset jdrk:58
-- comment 迁移向导与无损替换菜单及权限（FR-IO-06、FR-MIG-01~06）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4260, '迁移与无损替换', 4000, 2, '/env/migration', 'EnvMigration', 'env/migration/index', NULL, 'swap', b'0', b'0', b'0', NULL, 26, 1, 1, NOW()),
(4261, '检测原库', 4260, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:migration:detect', 1, 1, 1, NOW()),
(4262, '数据迁移', 4260, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:migration:import', 2, 1, 1, NOW()),
(4263, '一致性校验', 4260, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:migration:verify', 3, 1, 1, NOW()),
(4264, '并存与切换', 4260, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:migration:switch', 4, 1, 1, NOW());

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4260), (2, 4261), (2, 4262), (2, 4263), (2, 4264),
(547888897925840927, 4260), (547888897925840927, 4261), (547888897925840927, 4262),
(547888897925840927, 4263), (547888897925840927, 4264);
