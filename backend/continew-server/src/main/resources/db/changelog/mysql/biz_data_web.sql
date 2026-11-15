-- liquibase formatted sql

-- changeset jdrk:43
-- comment 新增历史数据与实时数据菜单及权限（FR-WEB-01/03/05/06）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4190, '历史数据', 4000, 2, '/env/data', 'EnvData', 'env/data/index', NULL, 'bar-chart', b'0', b'0', b'0', NULL, 19, 1, 1, NOW()),
(4191, '查询', 4190, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:data:list', 1, 1, 1, NOW()),
(4192, '详情', 4190, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:data:get', 2, 1, 1, NOW()),
(4193, '导出', 4190, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:data:export', 3, 1, 1, NOW()),
(4195, '实时数据', 4000, 2, '/env/data-latest', 'EnvDataLatest', 'env/data-latest/index', NULL, 'line-chart', b'0', b'0', b'0', NULL, 20, 1, 1, NOW()),
(4196, '查询', 4195, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:dataLatest:list', 1, 1, 1, NOW()),
(4197, '详情', 4195, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:dataLatest:get', 2, 1, 1, NOW());

-- changeset jdrk:44
-- comment 将历史数据与实时数据菜单授予「系统管理员」与「测试人员」角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4190), (2, 4191), (2, 4192), (2, 4193),
(547888897925840927, 4190), (547888897925840927, 4191), (547888897925840927, 4192), (547888897925840927, 4193),
(2, 4195), (2, 4196), (2, 4197),
(547888897925840927, 4195), (547888897925840927, 4196), (547888897925840927, 4197);
