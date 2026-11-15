-- liquibase formatted sql

-- changeset jdrk:39
-- comment 补充采集内核的可配置参数（FR-ACQ-02/08、FR-PROTO-07）
INSERT INTO `biz_sys_config`
(`category`, `name`, `code`, `value`, `default_value`, `description`, `create_user`, `create_time`)
VALUES
('COLLECT', '单设备轮询预算', 'COLLECT_POLL_BUDGET_MS', '3000', '3000', '单设备一轮轮询的最大耗时（毫秒），超预算跳过剩余通道（FR-ACQ-02）', 1, NOW()),
('COLLECT', '熔断时长', 'COLLECT_CIRCUIT_OPEN_MS', '60000', '60000', '连续失败熔断后暂停采集的时长（毫秒，FR-ACQ-09）', 1, NOW()),
('COLLECT', '退避上限', 'COLLECT_MAX_BACKOFF_MS', '300000', '300000', '失败退避的间隔上限（毫秒，FR-PROTO-07）', 1, NOW()),
('COLLECT', '夜间间隔倍数', 'COLLECT_NIGHT_MULTIPLIER', '1', '1', '夜间采集间隔倍数，1 表示不降频（FR-ACQ-08）', 1, NOW()),
('COLLECT', '夜间起始小时', 'COLLECT_NIGHT_START_HOUR', '22', '22', '夜间降频区间起始小时（0~23）', 1, NOW()),
('COLLECT', '夜间结束小时', 'COLLECT_NIGHT_END_HOUR', '6', '6', '夜间降频区间结束小时（0~23，可与起始小时跨零点）', 1, NOW());
