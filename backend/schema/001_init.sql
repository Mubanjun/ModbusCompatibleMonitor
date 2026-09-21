-- ============================================================
-- JDRK 水质监控平台 · 目标库初始化脚本（MySQL 8.4.11）
-- 幂等键：UNIQUE(record_time, device_sn, channel_no) → 支撑 UPSERT 去重
-- 注意：MySQL 8.4 默认认证插件为 caching_sha2_password，建账号不要指定
--       mysql_native_password（8.4 已默认禁用，会报 ERROR 1524）。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `env_monitor`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `env_monitor`;

-- ---------- 1. 设备（主机 / 站点） ----------
CREATE TABLE IF NOT EXISTS `tb_device` (
  `id`          INT UNSIGNED      NOT NULL AUTO_INCREMENT,
  `device_sn`   VARCHAR(32)       NOT NULL                COMMENT '设备唯一标识',
  `device_name` VARCHAR(64)       NOT NULL DEFAULT ''     COMMENT '设备名称',
  `location`    VARCHAR(128)      NOT NULL DEFAULT ''     COMMENT '安装位置',
  `protocol`    VARCHAR(16)       NOT NULL DEFAULT 'rs_modbus' COMMENT 'rs_modbus / std_modbus',
  `slave_addr`  SMALLINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '标准 ModBus 从站地址',
  `enabled`     TINYINT(1)        NOT NULL DEFAULT 1,
  `created_at`  DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_sn` (`device_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='监控主机（设备/站点）';

-- ---------- 2. 通道档案 ----------
CREATE TABLE IF NOT EXISTS `tb_channel` (
  `id`           INT UNSIGNED      NOT NULL AUTO_INCREMENT,
  `device_sn`    VARCHAR(32)       NOT NULL,
  `channel_no`   SMALLINT UNSIGNED NOT NULL              COMMENT '通道号 1~32',
  `slave_addr`   SMALLINT UNSIGNED NOT NULL              COMMENT 'RS-Modbus：从站地址=通道号',
  `channel_name` VARCHAR(64)       NOT NULL DEFAULT ''     COMMENT '如 溶解氧 / pH',
  `sensor_model` VARCHAR(64)       NOT NULL DEFAULT ''     COMMENT '如 RS-LDO-N01-2',
  `unit`         VARCHAR(16)       NOT NULL DEFAULT ''     COMMENT '如 mg/L / pH / NTU',
  `data_type`    VARCHAR(16)       NOT NULL DEFAULT 'ai1'
                                 COMMENT 'ai1/ai2/ai1_ai2/u32/i32/f32/switch',
  `coef_a`       DECIMAL(18,6)     NOT NULL DEFAULT 1.000000 COMMENT '显示值=原始值*A+B',
  `coef_b`       DECIMAL(18,6)     NOT NULL DEFAULT 0.000000,
  `decimals`     TINYINT UNSIGNED  NOT NULL DEFAULT 2,
  `upper_limit`  DECIMAL(18,6)     NULL,
  `lower_limit`  DECIMAL(18,6)     NULL,
  `enabled`      TINYINT(1)        NOT NULL DEFAULT 1,
  `created_at`   DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`   DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dev_channel` (`device_sn`, `channel_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通道档案（20 路传感器）';

-- ---------- 3. 时序数据主表（长表） ----------
CREATE TABLE IF NOT EXISTS `tb_sensor_data` (
  `id`          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  `record_time` DATETIME(3)       NOT NULL                 COMMENT '采集时间（同一轮各通道一致）',
  `device_sn`   VARCHAR(32)       NOT NULL,
  `channel_no`  SMALLINT UNSIGNED NOT NULL,
  `value`       DECIMAL(18,4)     NULL                     COMMENT '工程量（换算后）',
  `raw_value`   BIGINT            NULL                     COMMENT '原始值（核对系数 A/B）',
  `quality`     VARCHAR(16)       NOT NULL DEFAULT 'ok'
                                 COMMENT 'ok/timeout/crc_error/illegal_addr/slave_fault/scaling/no_data',
  `rtt_ms`      SMALLINT UNSIGNED NULL                     COMMENT '链路往返耗时(ms)',
  `created_at`  DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_time_dev_ch` (`record_time`, `device_sn`, `channel_no`),
  KEY `idx_dev_ch_time` (`device_sn`, `channel_no`, `record_time`),
  KEY `idx_record_time` (`record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='传感器时序数据（长表）';

-- ---------- 4. 告警记录 ----------
CREATE TABLE IF NOT EXISTS `tb_alarm` (
  `id`          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  `device_sn`   VARCHAR(32)       NOT NULL,
  `channel_no`  SMALLINT UNSIGNED NOT NULL,
  `channel_name` VARCHAR(64)      NOT NULL DEFAULT '',
  `alarm_type`  VARCHAR(16)       NOT NULL                 COMMENT 'high / low',
  `value`       DECIMAL(18,4)     NULL,
  `threshold`   DECIMAL(18,4)     NULL,
  `raised_at`   DATETIME(3)       NOT NULL,
  `cleared_at`  DATETIME(3)       NULL,
  `created_at`  DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_dev_ch_raised` (`device_sn`, `channel_no`, `raised_at`),
  KEY `idx_raised_at` (`raised_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='告警记录';

-- ---------- 5. 回传运行日志 ----------
CREATE TABLE IF NOT EXISTS `tb_forward_log` (
  `id`          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  `sink_name`   VARCHAR(64)       NOT NULL,
  `batch_count` INT UNSIGNED      NOT NULL DEFAULT 0,
  `ok_count`    INT UNSIGNED      NOT NULL DEFAULT 0,
  `fail_count`  INT UNSIGNED      NOT NULL DEFAULT 0,
  `elapsed_ms`  INT UNSIGNED      NULL,
  `message`     VARCHAR(512)      NOT NULL DEFAULT '',
  `created_at`  DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_sink_time` (`sink_name`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='回传运行日志';

-- ---------- 6. 账号（可选；请 DBA 执行） ----------
-- CREATE USER IF NOT EXISTS 'monitor_rw'@'%' IDENTIFIED BY '<强密码>';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON `env_monitor`.* TO 'monitor_rw'@'%';
-- FLUSH PRIVILEGES;
