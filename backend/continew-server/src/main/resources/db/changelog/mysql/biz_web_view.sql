-- liquibase formatted sql

-- changeset jdrk:60
-- comment 实时曲线与多视图大屏菜单及权限（FR-WEB-02/04/07）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4270, '实时曲线', 4000, 2, '/env/realtime-curve', 'EnvRealtimeCurve', 'env/realtime-curve/index', NULL, 'line-chart', b'0', b'0', b'0', NULL, 21, 1, 1, NOW()),
(4271, '多视图大屏', 4000, 2, '/env/multi-view', 'EnvMultiView', 'env/multi-view/index', NULL, 'dashboard', b'0', b'0', b'0', NULL, 22, 1, 1, NOW()),
(4272, '查询', 4270, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:data:list', 1, 1, 1, NOW()),
(4273, '查询', 4271, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:dataLatest:list', 1, 1, 1, NOW());

-- changeset jdrk:61
-- comment 将实时曲线与多视图大屏菜单授予「系统管理员」与「测试人员」角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4270), (2, 4271), (2, 4272), (2, 4273),
(547888897925840927, 4270), (547888897925840927, 4271), (547888897925840927, 4272), (547888897925840927, 4273);

-- changeset jdrk:62
-- comment 登记停机权限（FR-SYS-06）：原先 monitor:system:shutdown 只存在于代码注解里，
--         除超管外任何角色都无法被授予；登记为「看门狗与自检」下的按钮权限后可按需授权
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4274, '停机', 4250, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:system:shutdown', 3, 1, 1, NOW());

-- changeset jdrk:63
-- comment 停机权限只授予「系统管理员」，测试角色不授予（避免误停机）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2, 4274);

-- changeset jdrk:64
-- comment 清理重复权限：4272/4273 的「查询」按钮权限（monitor:data:list / monitor:dataLatest:list）
--         与历史数据的 4191、实时数据的 4196 完全重复，统一由原菜单持有，避免同一权限出现两行
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (4272, 4273);
DELETE FROM `sys_menu` WHERE `id` IN (4272, 4273);
