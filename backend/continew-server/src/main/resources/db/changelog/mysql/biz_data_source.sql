-- liquibase formatted sql

-- changeset jdrk:42
-- comment 历史数据表补充来源主键，用于暂存上传的幂等去重（NFR-REL-05）
ALTER TABLE `biz_data`
    ADD COLUMN `source_id` bigint DEFAULT NULL COMMENT '来源暂存记录 ID（幂等去重）' AFTER `record_time`;

CREATE UNIQUE INDEX `uk_data_source_id` ON `biz_data`(`source_id`);
