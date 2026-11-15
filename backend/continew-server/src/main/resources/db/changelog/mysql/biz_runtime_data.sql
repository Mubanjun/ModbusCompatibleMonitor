-- liquibase formatted sql

-- changeset jdrk:40
-- comment 新增采集运行与信令监视菜单及权限（FR-ACQ-08/10、FR-DEV-13、FR-SIG-01/02）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4170, '采集运行', 4000, 2, '/env/acquisition', 'EnvAcquisition', 'env/acquisition/index', NULL, 'dashboard', b'0', b'0', b'0', NULL, 17, 1, 1, NOW()),
(4171, '状态', 4170, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:acquisition:status', 1, 1, 1, NOW()),
(4172, '启停控制', 4170, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:acquisition:control', 2, 1, 1, NOW()),
(4173, '通讯测试', 4170, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:acquisition:test', 3, 1, 1, NOW()),
(4180, '信令监视', 4000, 2, '/env/signal', 'EnvSignal', 'env/signal/index', NULL, 'swap', b'0', b'0', b'0', NULL, 18, 1, 1, NOW()),
(4181, '列表', 4180, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:signalLog:list', 1, 1, 1, NOW()),
(4182, '详情', 4180, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:signalLog:get', 2, 1, 1, NOW());

-- changeset jdrk:41
-- comment 将采集运行与信令监视菜单授予「系统管理员」与「测试人员」角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4170), (2, 4171), (2, 4172), (2, 4173),
(547888897925840927, 4170), (547888897925840927, 4171), (547888897925840927, 4172), (547888897925840927, 4173),
(2, 4180), (2, 4181), (2, 4182),
(547888897925840927, 4180), (547888897925840927, 4181), (547888897925840927, 4182);
