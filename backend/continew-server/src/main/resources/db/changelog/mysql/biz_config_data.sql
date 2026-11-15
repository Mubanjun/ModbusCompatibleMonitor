-- liquibase formatted sql

-- changeset jdrk:29
-- comment 初始化业务系统配置默认项（FR-CFG-01~04 与 FR-WD 相关参数）
INSERT INTO `biz_sys_config`
(`category`, `name`, `code`, `value`, `default_value`, `description`, `create_user`, `create_time`)
VALUES
('COLLECT', '采集间隔', 'COLLECT_INTERVAL_MS', '5000', '5000', '全局采集间隔（毫秒），设备与端口级配置优先', 1, NOW()),
('COLLECT', '单帧超时', 'COLLECT_TIMEOUT_MS', '1000', '1000', '单帧通讯超时（毫秒）', 1, NOW()),
('COLLECT', '失败重试次数', 'COLLECT_RETRY_COUNT', '3', '3', '单次采集失败的重试次数', 1, NOW()),
('COLLECT', '熔断阈值', 'COLLECT_FUSE_THRESHOLD', '5', '5', '连续失败达到该次数将设备置为异常（FR-ACQ-09）', 1, NOW()),
('REPORT', '上报间隔', 'REPORT_INTERVAL_MS', '60000', '60000', '默认上报间隔（毫秒）', 1, NOW()),
('REPORT', '上报重试次数', 'REPORT_RETRY_COUNT', '3', '3', '上报失败的重试次数，超限进入死信队列', 1, NOW()),
('STORAGE', '数据保留天数', 'STORAGE_RETAIN_DAYS', '90', '90', '历史数据保留天数，超期滚动清理', 1, NOW()),
('STORAGE', '暂存上限行数', 'STORAGE_BUFFER_MAX_ROWS', '1000000', '1000000', '本地暂存上限，达到后按时间清理最旧数据', 1, NOW()),
('SYSTEM', '看门狗心跳间隔', 'WATCHDOG_HEARTBEAT_S', '30', '30', '后端写心跳的间隔（秒）', 1, NOW()),
('SYSTEM', '看门狗超时阈值', 'WATCHDOG_TIMEOUT_S', '120', '120', '超过该时长未收到心跳判定为失联（秒）', 1, NOW()),
('SYSTEM', '重启限流上限', 'WATCHDOG_RESTART_LIMIT_PER_HOUR', '5', '5', '每小时最大重启次数，超限停机告警（FR-WD-05）', 1, NOW());
