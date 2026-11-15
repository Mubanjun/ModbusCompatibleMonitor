-- liquibase formatted sql

-- changeset jdrk:4
-- comment 初始化采集配置菜单与权限（环境监测业务域）
-- 说明：菜单 ID 取 4000 段。已占用区间为 1000~2053（基座）、3000~3025（租户插件）、
--      7000~7018（能力开放）、8000~8023（任务调度）、9000~9014（代码生成）。
--      前端路由前缀用 /env，因为 /monitor 已被基座的「系统监控」目录占用；
--      后端 REST 路径仍是 /monitor/*，二者是不同的命名空间。
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4000, '环境监测', 0, 1, '/env', 'EnvMonitor', 'Layout', '/env/port', 'cloud', b'0', b'0', b'0', NULL, 3, 1, 1, NOW()),

(4010, '端口管理', 4000, 2, '/env/port', 'EnvPort', 'env/port/index', NULL, 'storage', b'0', b'0', b'0', NULL, 1, 1, 1, NOW()),
(4011, '列表', 4010, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:port:list', 1, 1, 1, NOW()),
(4012, '详情', 4010, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:port:get', 2, 1, 1, NOW()),
(4013, '新增', 4010, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:port:create', 3, 1, 1, NOW()),
(4014, '修改', 4010, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:port:update', 4, 1, 1, NOW()),
(4015, '删除', 4010, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:port:delete', 5, 1, 1, NOW()),

(4020, '设备管理', 4000, 2, '/env/device', 'EnvDevice', 'env/device/index', NULL, 'computer', b'0', b'0', b'0', NULL, 2, 1, 1, NOW()),
(4021, '列表', 4020, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:device:list', 1, 1, 1, NOW()),
(4022, '详情', 4020, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:device:get', 2, 1, 1, NOW()),
(4023, '新增', 4020, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:device:create', 3, 1, 1, NOW()),
(4024, '修改', 4020, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:device:update', 4, 1, 1, NOW()),
(4025, '删除', 4020, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:device:delete', 5, 1, 1, NOW()),

(4030, '通道管理', 4000, 2, '/env/channel', 'EnvChannel', 'env/channel/index', NULL, 'mind-mapping', b'0', b'0', b'0', NULL, 3, 1, 1, NOW()),
(4031, '列表', 4030, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:channel:list', 1, 1, 1, NOW()),
(4032, '详情', 4030, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:channel:get', 2, 1, 1, NOW()),
(4033, '新增', 4030, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:channel:create', 3, 1, 1, NOW()),
(4034, '修改', 4030, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:channel:update', 4, 1, 1, NOW()),
(4035, '删除', 4030, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:channel:delete', 5, 1, 1, NOW());

-- changeset jdrk:5
-- comment 将采集配置菜单授予「系统管理员」与「测试人员」角色（超级管理员自动拥有全部菜单）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4000), (2, 4010), (2, 4011), (2, 4012), (2, 4013), (2, 4014), (2, 4015),
(2, 4020), (2, 4021), (2, 4022), (2, 4023), (2, 4024), (2, 4025),
(2, 4030), (2, 4031), (2, 4032), (2, 4033), (2, 4034), (2, 4035),
(547888897925840927, 4000), (547888897925840927, 4010), (547888897925840927, 4011), (547888897925840927, 4012),
(547888897925840927, 4013), (547888897925840927, 4014), (547888897925840927, 4015),
(547888897925840927, 4020), (547888897925840927, 4021), (547888897925840927, 4022), (547888897925840927, 4023),
(547888897925840927, 4024), (547888897925840927, 4025),
(547888897925840927, 4030), (547888897925840927, 4031), (547888897925840927, 4032), (547888897925840927, 4033),
(547888897925840927, 4034), (547888897925840927, 4035);
