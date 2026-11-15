-- liquibase formatted sql

-- changeset jdrk:32
-- comment 业务配置菜单改名：基座在「系统管理」下已有同名菜单「系统配置」，重名会在侧边栏造成混淆
UPDATE `sys_menu` SET `title` = '业务配置' WHERE `id` = 4110;
