-- liquibase formatted sql

-- changeset jdrk:50
-- comment 新增同步任务执行/暂停/续传权限（FR-SYNC-06）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4210, '执行同步', 4100, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncTask:start', 6, 1, 1, NOW()),
(4211, '暂停同步', 4100, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncTask:pause', 7, 1, 1, NOW()),
(4212, '续传同步', 4100, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:syncTask:resume', 8, 1, 1, NOW());

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4210), (2, 4211), (2, 4212),
(547888897925840927, 4210), (547888897925840927, 4211), (547888897925840927, 4212);
