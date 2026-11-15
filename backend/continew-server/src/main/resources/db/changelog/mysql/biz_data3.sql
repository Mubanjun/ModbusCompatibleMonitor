-- liquibase formatted sql

-- changeset jdrk:21
-- comment 新增报警规则菜单与权限
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4050, '报警规则', 4000, 2, '/env/alarm', 'EnvAlarm', 'env/alarm/index', NULL, 'exclamation-circle', b'0', b'0', b'0', NULL, 5, 1, 1, NOW()),
(4051, '列表', 4050, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:alarm:list', 1, 1, 1, NOW()),
(4052, '详情', 4050, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:alarm:get', 2, 1, 1, NOW()),
(4053, '新增', 4050, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:alarm:create', 3, 1, 1, NOW()),
(4054, '修改', 4050, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:alarm:update', 4, 1, 1, NOW()),
(4055, '删除', 4050, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:alarm:delete', 5, 1, 1, NOW());

-- changeset jdrk:22
-- comment 将报警规则菜单授予「系统管理员」与「测试人员」角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4050), (2, 4051), (2, 4052), (2, 4053), (2, 4054), (2, 4055),
(547888897925840927, 4050), (547888897925840927, 4051), (547888897925840927, 4052),
(547888897925840927, 4053), (547888897925840927, 4054), (547888897925840927, 4055);
