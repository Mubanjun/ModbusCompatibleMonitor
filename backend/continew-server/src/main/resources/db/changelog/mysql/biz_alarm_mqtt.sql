-- liquibase formatted sql

-- changeset jdrk:49
-- comment 补充报警 MQTT 通知的 Broker 配置（FR-ALM-04 与 FR-RPT-01 共用）
INSERT INTO `biz_sys_config`
(`category`, `name`, `code`, `value`, `default_value`, `description`, `create_user`, `create_time`)
VALUES
('ALARM', 'MQTT Broker 地址', 'ALARM_NOTIFY_MQTT_HOST', '127.0.0.1', '127.0.0.1', '报警 MQTT 通知的 Broker 地址', 1, NOW()),
('ALARM', 'MQTT Broker 端口', 'ALARM_NOTIFY_MQTT_PORT', '1883', '1883', '报警 MQTT 通知的 Broker 端口', 1, NOW()),
('ALARM', 'MQTT 用户名', 'ALARM_NOTIFY_MQTT_USERNAME', '', '', '报警 MQTT 通知用户名，留空表示匿名', 1, NOW()),
('ALARM', 'MQTT 口令', 'ALARM_NOTIFY_MQTT_PASSWORD', '', '', '报警 MQTT 通知口令，留空表示匿名', 1, NOW());
