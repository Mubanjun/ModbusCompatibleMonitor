-- liquibase formatted sql

-- changeset jdrk:23
-- comment 新增上报目标与上报模板菜单及权限
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4060, '上报目标', 4000, 2, '/env/report-target', 'EnvReportTarget', 'env/report-target/index', NULL, 'cloud', b'0', b'0', b'0', NULL, 6, 1, 1, NOW()),
(4061, '列表', 4060, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportTarget:list', 1, 1, 1, NOW()),
(4062, '详情', 4060, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportTarget:get', 2, 1, 1, NOW()),
(4063, '新增', 4060, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportTarget:create', 3, 1, 1, NOW()),
(4064, '修改', 4060, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportTarget:update', 4, 1, 1, NOW()),
(4065, '删除', 4060, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportTarget:delete', 5, 1, 1, NOW()),
(4070, '上报模板', 4000, 2, '/env/report-template', 'EnvReportTemplate', 'env/report-template/index', NULL, 'code', b'0', b'0', b'0', NULL, 7, 1, 1, NOW()),
(4071, '列表', 4070, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportTemplate:list', 1, 1, 1, NOW()),
(4072, '详情', 4070, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportTemplate:get', 2, 1, 1, NOW()),
(4073, '新增', 4070, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportTemplate:create', 3, 1, 1, NOW()),
(4074, '修改', 4070, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportTemplate:update', 4, 1, 1, NOW()),
(4075, '删除', 4070, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportTemplate:delete', 5, 1, 1, NOW());

-- changeset jdrk:24
-- comment 将上报目标与上报模板菜单授予「系统管理员」与「测试人员」角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4060), (2, 4061), (2, 4062), (2, 4063), (2, 4064), (2, 4065),
(2, 4070), (2, 4071), (2, 4072), (2, 4073), (2, 4074), (2, 4075),
(547888897925840927, 4060), (547888897925840927, 4061), (547888897925840927, 4062),
(547888897925840927, 4063), (547888897925840927, 4064), (547888897925840927, 4065),
(547888897925840927, 4070), (547888897925840927, 4071), (547888897925840927, 4072),
(547888897925840927, 4073), (547888897925840927, 4074), (547888897925840927, 4075);
