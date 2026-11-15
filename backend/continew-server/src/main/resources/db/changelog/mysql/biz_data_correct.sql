-- liquibase formatted sql

-- changeset jdrk:59
-- comment 历史数据修正权限（FR-WEB-08 数据补算与修正）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4194, '数据修正', 4190, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:data:correct', 4, 1, 1, NOW());

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4194), (547888897925840927, 4194);
