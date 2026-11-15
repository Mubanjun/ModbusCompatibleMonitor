-- liquibase formatted sql

-- changeset jdrk:19
-- comment 新增设备模板菜单与权限
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4040, '设备模板', 4000, 2, '/env/template', 'EnvTemplate', 'env/template/index', NULL, 'settings', b'0', b'0', b'0', NULL, 4, 1, 1, NOW()),
(4041, '列表', 4040, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:template:list', 1, 1, 1, NOW()),
(4042, '详情', 4040, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:template:get', 2, 1, 1, NOW()),
(4043, '新增', 4040, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:template:create', 3, 1, 1, NOW()),
(4044, '修改', 4040, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:template:update', 4, 1, 1, NOW()),
(4045, '删除', 4040, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:template:delete', 5, 1, 1, NOW());

-- changeset jdrk:20
-- comment 将设备模板菜单授予「系统管理员」与「测试人员」角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4040), (2, 4041), (2, 4042), (2, 4043), (2, 4044), (2, 4045),
(547888897925840927, 4040), (547888897925840927, 4041), (547888897925840927, 4042),
(547888897925840927, 4043), (547888897925840927, 4044), (547888897925840927, 4045);
