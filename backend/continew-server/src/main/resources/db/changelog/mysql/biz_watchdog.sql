-- liquibase formatted sql

-- changeset jdrk:57
-- comment 看门狗与自检菜单及权限（FR-WD-02/05/06）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4250, '看门狗与自检', 4000, 2, '/env/watchdog', 'EnvWatchdog', 'env/watchdog/index', NULL, 'safe', b'0', b'0', b'0', NULL, 25, 1, 1, NOW()),
(4251, '状态查看', 4250, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:watchdog:status', 1, 1, 1, NOW()),
(4252, '执行自检', 4250, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:watchdog:selfCheck', 2, 1, 1, NOW());

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4250), (2, 4251), (2, 4252),
(547888897925840927, 4250), (547888897925840927, 4251), (547888897925840927, 4252);
