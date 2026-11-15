-- liquibase formatted sql

-- changeset jdrk:30
-- comment 新增业务系统配置菜单及权限
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4110, '系统配置', 4000, 2, '/env/config', 'EnvConfig', 'env/config/index', NULL, 'settings', b'0', b'0', b'0', NULL, 11, 1, 1, NOW()),
(4111, '列表', 4110, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:config:list', 1, 1, 1, NOW()),
(4112, '详情', 4110, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:config:get', 2, 1, 1, NOW()),
(4113, '新增', 4110, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:config:create', 3, 1, 1, NOW()),
(4114, '修改', 4110, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:config:update', 4, 1, 1, NOW()),
(4115, '删除', 4110, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:config:delete', 5, 1, 1, NOW());

-- changeset jdrk:31
-- comment 将业务系统配置菜单授予「系统管理员」与「测试人员」角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4110), (2, 4111), (2, 4112), (2, 4113), (2, 4114), (2, 4115),
(547888897925840927, 4110), (547888897925840927, 4111), (547888897925840927, 4112),
(547888897925840927, 4113), (547888897925840927, 4114), (547888897925840927, 4115);
