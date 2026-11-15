-- liquibase formatted sql

-- changeset jdrk:45
-- comment 补充报警通知目标配置（FR-ALM-04）
INSERT INTO `biz_sys_config`
(`category`, `name`, `code`, `value`, `default_value`, `description`, `create_user`, `create_time`)
VALUES
('ALARM', '短信通知目标', 'ALARM_NOTIFY_SMS_TARGET', '', '', '报警短信通知手机号，多个用英文逗号分隔（FR-ALM-04）', 1, NOW()),
('ALARM', '邮件通知目标', 'ALARM_NOTIFY_EMAIL_TARGET', '', '', '报警邮件通知邮箱，多个用英文逗号分隔（FR-ALM-04）', 1, NOW()),
('ALARM', 'MQTT 通知主题', 'ALARM_NOTIFY_MQTT_TOPIC', 'jdrk/alarm', 'jdrk/alarm', '报警 MQTT 通知主题，支持变量占位', 1, NOW());

-- changeset jdrk:46
-- comment 新增报警处理权限并授予角色（FR-ALM-03）
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4123, '处理', 4120, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:alarmRecord:update', 3, 1, 1, NOW());

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 4123), (547888897925840927, 4123);
