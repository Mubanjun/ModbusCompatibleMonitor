-- liquibase formatted sql

-- changeset jdrk:55
-- comment 日志与诊断菜单及权限（FR-LOG-01/03/04）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4230, '日志与诊断', 4000, 2, '/env/log', 'EnvLog', 'env/log/index', NULL, 'file', b'0', b'0', b'0', NULL, 23, 1, 1, NOW()),
(4231, '级别管理', 4230, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:log:level', 1, 1, 1, NOW()),
(4232, '日志文件', 4230, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:log:file', 2, 1, 1, NOW()),
(4233, '诊断包导出', 4230, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:diagnostic:export', 3, 1, 1, NOW());

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4230), (2, 4231), (2, 4232), (2, 4233),
(547888897925840927, 4230), (547888897925840927, 4231), (547888897925840927, 4232), (547888897925840927, 4233);
