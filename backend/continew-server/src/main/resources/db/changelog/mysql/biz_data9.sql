-- liquibase formatted sql

-- changeset jdrk:33
-- comment 新增运行时可观测数据菜单与只读权限（报警记录/报警通知/同步冲突/同步水位/看门狗日志）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4120, '报警记录', 4000, 2, '/env/alarm-record', 'EnvAlarmRecord', 'env/alarm-record/index', NULL, 'exclamation-circle', b'0', b'0', b'0', NULL, 12, 1, 1, NOW()),
(4121, '列表', 4120, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:alarmRecord:list', 1, 1, 1, NOW()),
(4122, '详情', 4120, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:alarmRecord:get', 2, 1, 1, NOW()),
(4130, '报警通知', 4000, 2, '/env/alarm-notify', 'EnvAlarmNotify', 'env/alarm-notify/index', NULL, 'email', b'0', b'0', b'0', NULL, 13, 1, 1, NOW()),
(4131, '列表', 4130, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:alarmNotify:list', 1, 1, 1, NOW()),
(4132, '详情', 4130, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:alarmNotify:get', 2, 1, 1, NOW()),
(4140, '同步冲突', 4000, 2, '/env/sync-conflict', 'EnvSyncConflict', 'env/sync-conflict/index', NULL, 'exclamation-circle', b'0', b'0', b'0', NULL, 14, 1, 1, NOW()),
(4141, '列表', 4140, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncConflict:list', 1, 1, 1, NOW()),
(4142, '详情', 4140, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncConflict:get', 2, 1, 1, NOW()),
(4150, '同步水位', 4000, 2, '/env/sync-watermark', 'EnvSyncWatermark', 'env/sync-watermark/index', NULL, 'sync', b'0', b'0', b'0', NULL, 15, 1, 1, NOW()),
(4151, '列表', 4150, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncWatermark:list', 1, 1, 1, NOW()),
(4152, '详情', 4150, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncWatermark:get', 2, 1, 1, NOW()),
(4160, '看门狗日志', 4000, 2, '/env/watchdog-log', 'EnvWatchdogLog', 'env/watchdog-log/index', NULL, 'monitor', b'0', b'0', b'0', NULL, 16, 1, 1, NOW()),
(4161, '列表', 4160, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:watchdogLog:list', 1, 1, 1, NOW()),
(4162, '详情', 4160, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:watchdogLog:get', 2, 1, 1, NOW());

-- changeset jdrk:34
-- comment 将运行时数据菜单授予「系统管理员」与「测试人员」角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4120), (2, 4121), (2, 4122),
(547888897925840927, 4120), (547888897925840927, 4121), (547888897925840927, 4122),
(2, 4130), (2, 4131), (2, 4132),
(547888897925840927, 4130), (547888897925840927, 4131), (547888897925840927, 4132),
(2, 4140), (2, 4141), (2, 4142),
(547888897925840927, 4140), (547888897925840927, 4141), (547888897925840927, 4142),
(2, 4150), (2, 4151), (2, 4152),
(547888897925840927, 4150), (547888897925840927, 4151), (547888897925840927, 4152),
(2, 4160), (2, 4161), (2, 4162),
(547888897925840927, 4160), (547888897925840927, 4161), (547888897925840927, 4162);
