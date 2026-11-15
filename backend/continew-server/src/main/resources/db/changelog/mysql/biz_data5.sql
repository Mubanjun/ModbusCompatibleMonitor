-- liquibase formatted sql

-- changeset jdrk:25
-- comment 新增上报规则与采集顺序菜单及权限
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4080, '上报规则', 4000, 2, '/env/report-rule', 'EnvReportRule', 'env/report-rule/index', NULL, 'send', b'0', b'0', b'0', NULL, 8, 1, 1, NOW()),
(4081, '列表', 4080, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportRule:list', 1, 1, 1, NOW()),
(4082, '详情', 4080, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportRule:get', 2, 1, 1, NOW()),
(4083, '新增', 4080, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportRule:create', 3, 1, 1, NOW()),
(4084, '修改', 4080, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportRule:update', 4, 1, 1, NOW()),
(4085, '删除', 4080, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:reportRule:delete', 5, 1, 1, NOW()),
(4090, '采集顺序', 4000, 2, '/env/collect-order', 'EnvCollectOrder', 'env/collect-order/index', NULL, 'sort', b'0', b'0', b'0', NULL, 9, 1, 1, NOW()),
(4091, '列表', 4090, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:collectOrder:list', 1, 1, 1, NOW()),
(4092, '详情', 4090, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:collectOrder:get', 2, 1, 1, NOW()),
(4093, '新增', 4090, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:collectOrder:create', 3, 1, 1, NOW()),
(4094, '修改', 4090, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:collectOrder:update', 4, 1, 1, NOW()),
(4095, '删除', 4090, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:collectOrder:delete', 5, 1, 1, NOW());

-- changeset jdrk:26
-- comment 将上报规则与采集顺序菜单授予「系统管理员」与「测试人员」角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4080), (2, 4081), (2, 4082), (2, 4083), (2, 4084), (2, 4085),
(2, 4090), (2, 4091), (2, 4092), (2, 4093), (2, 4094), (2, 4095),
(547888897925840927, 4080), (547888897925840927, 4081), (547888897925840927, 4082),
(547888897925840927, 4083), (547888897925840927, 4084), (547888897925840927, 4085),
(547888897925840927, 4090), (547888897925840927, 4091), (547888897925840927, 4092),
(547888897925840927, 4093), (547888897925840927, 4094), (547888897925840927, 4095);
