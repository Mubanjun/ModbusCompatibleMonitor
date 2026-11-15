-- liquibase formatted sql

-- changeset jdrk:27
-- comment 新增同步任务菜单及权限
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4100, '同步任务', 4000, 2, '/env/sync-task', 'EnvSyncTask', 'env/sync-task/index', NULL, 'sync', b'0', b'0', b'0', NULL, 10, 1, 1, NOW()),
(4101, '列表', 4100, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncTask:list', 1, 1, 1, NOW()),
(4102, '详情', 4100, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncTask:get', 2, 1, 1, NOW()),
(4103, '新增', 4100, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncTask:create', 3, 1, 1, NOW()),
(4104, '修改', 4100, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncTask:update', 4, 1, 1, NOW()),
(4105, '删除', 4100, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncTask:delete', 5, 1, 1, NOW());

-- changeset jdrk:28
-- comment 将同步任务菜单授予「系统管理员」与「测试人员」角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4100), (2, 4101), (2, 4102), (2, 4103), (2, 4104), (2, 4105),
(547888897925840927, 4100), (547888897925840927, 4101), (547888897925840927, 4102),
(547888897925840927, 4103), (547888897925840927, 4104), (547888897925840927, 4105);
