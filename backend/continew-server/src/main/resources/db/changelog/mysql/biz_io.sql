-- liquibase formatted sql

-- changeset jdrk:56
-- comment 数据导入导出与备份恢复菜单及权限（FR-IO-01~05）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4240, '导入导出与备份', 4000, 2, '/env/io', 'EnvIo', 'env/io/index', NULL, 'cloud', b'0', b'0', b'0', NULL, 24, 1, 1, NOW()),
(4241, '数据导入', 4240, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:io:import', 1, 1, 1, NOW()),
(4242, '数据导出', 4240, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:io:export', 2, 1, 1, NOW()),
(4243, '备份管理', 4240, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:io:backup', 3, 1, 1, NOW()),
(4244, '数据恢复', 4240, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:io:restore', 4, 1, 1, NOW());

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4240), (2, 4241), (2, 4242), (2, 4243), (2, 4244),
(547888897925840927, 4240), (547888897925840927, 4241), (547888897925840927, 4242),
(547888897925840927, 4243), (547888897925840927, 4244);
