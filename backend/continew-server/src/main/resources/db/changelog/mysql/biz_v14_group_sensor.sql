-- liquibase formatted sql

-- changeset jdrk:v14-1
-- comment V1.4 设备分组树
CREATE TABLE IF NOT EXISTS `biz_device_group` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '分组 ID',
    `parent_id`    bigint       NOT NULL DEFAULT 0     COMMENT '父分组 ID，根为 0',
    `name`         varchar(50)  NOT NULL               COMMENT '分组名称',
    `path`         varchar(500) NOT NULL DEFAULT ''    COMMENT '物化路径，如 /1/5/12/',
    `depth`        tinyint      NOT NULL DEFAULT 1     COMMENT '层级',
    `sort_no`      int          NOT NULL DEFAULT 999   COMMENT '同父排序',
    `device_count` int          NOT NULL DEFAULT 0     COMMENT '直接设备数',
    `is_builtin`   bit(1)       NOT NULL DEFAULT b'0'  COMMENT '是否内置',
    `remark`       varchar(200) DEFAULT NULL           COMMENT '备注',
    `create_user`  bigint       NOT NULL DEFAULT 0     COMMENT '创建人',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`  bigint       DEFAULT NULL           COMMENT '修改人',
    `update_time`  datetime     DEFAULT NULL           COMMENT '修改时间',
    `deleted`      bigint       NOT NULL DEFAULT 0     COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_parent_name_deleted` (`parent_id`, `name`, `deleted`),
    KEY `idx_group_path` (`path`(200)),
    KEY `idx_group_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备分组树';
-- changeset jdrk:v14-2
-- comment 设备归组与位置字段
ALTER TABLE `biz_device`
    ADD COLUMN `group_id` bigint NOT NULL DEFAULT 0 COMMENT '所属分组 ID' AFTER `model`,
    ADD COLUMN `elevation` decimal(8,2) DEFAULT NULL COMMENT '海拔（米）' AFTER `latitude`,
    ADD COLUMN `address` varchar(255) DEFAULT NULL COMMENT '逆地理编码/手工地址' AFTER `elevation`,
    ADD INDEX `idx_device_group_id` (`group_id`);
-- changeset jdrk:v14-3
-- comment 根分组与存量 group_name 迁移
INSERT INTO `biz_device_group` (`id`, `parent_id`, `name`, `path`, `depth`, `sort_no`, `device_count`, `is_builtin`, `create_user`, `create_time`, `deleted`)
SELECT 1, 0, '未分组', '/1/', 0, 0, 0, b'1', 0, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `biz_device_group` WHERE `id` = 1);
INSERT INTO `biz_device_group` (`parent_id`, `name`, `path`, `depth`, `sort_no`, `device_count`, `is_builtin`, `create_user`, `create_time`, `deleted`)
SELECT 1, t.`group_name`, '', 1, 999, 0, b'0', 0, NOW(), 0
FROM (SELECT DISTINCT `group_name` FROM `biz_device`
      WHERE `group_name` IS NOT NULL AND TRIM(`group_name`) <> '' AND `deleted` = 0) t
WHERE NOT EXISTS (SELECT 1 FROM `biz_device_group` g
                  WHERE g.`parent_id` = 1 AND g.`name` = t.`group_name` AND g.`deleted` = 0);
UPDATE `biz_device_group` SET `path` = CONCAT('/1/', `id`, '/') WHERE `path` = '' AND `depth` = 1;
UPDATE `biz_device` d JOIN `biz_device_group` g ON g.`parent_id` = 1 AND g.`name` = d.`group_name`
SET d.`group_id` = g.`id` WHERE d.`group_id` = 0 AND d.`group_name` IS NOT NULL AND TRIM(d.`group_name`) <> '';
UPDATE `biz_device` SET `group_id` = 1 WHERE `group_id` = 0;
UPDATE `biz_device_group` g SET g.`device_count` = (SELECT COUNT(*) FROM `biz_device` d WHERE d.`group_id` = g.`id` AND d.`deleted` = 0);
-- changeset jdrk:v14-4
-- comment 对象化传感器与观测值窄表
CREATE TABLE IF NOT EXISTS `biz_sensor` (
    `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '传感器对象 ID',
    `name`         varchar(100)  NOT NULL               COMMENT '传感器名称',
    `group_id`     bigint        NOT NULL DEFAULT 0     COMMENT '所属分组',
    `port_id`      bigint        NOT NULL               COMMENT '所属端口',
    `device_id`    bigint        DEFAULT NULL           COMMENT '所属设备（直连传感器为空）',
    `channel_no`   int           DEFAULT NULL           COMMENT '通道号',
    `analog_no`    tinyint       DEFAULT NULL           COMMENT '模拟量序号',
    `element_code` varchar(50)   NOT NULL               COMMENT '要素编码',
    `unit`         varchar(20)   DEFAULT NULL           COMMENT '单位',
    `range_min`    decimal(18,4) DEFAULT NULL           COMMENT '量程下限',
    `range_max`    decimal(18,4) DEFAULT NULL           COMMENT '量程上限',
    `coefficient`  decimal(18,6) NOT NULL DEFAULT 1      COMMENT '系数',
    `offset_value` decimal(18,6) NOT NULL DEFAULT 0      COMMENT '偏移',
    `precision`    tinyint       DEFAULT NULL           COMMENT '显示精度',
    `template_id`  bigint        DEFAULT NULL           COMMENT '设备模板',
    `enabled`      bit(1)        NOT NULL DEFAULT b'1'  COMMENT '是否启用',
    `create_user`  bigint        NOT NULL DEFAULT 0     COMMENT '创建人',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`  bigint        DEFAULT NULL           COMMENT '修改人',
    `update_time`  datetime      DEFAULT NULL           COMMENT '修改时间',
    `deleted`      bigint        NOT NULL DEFAULT 0     COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_sensor_group_id` (`group_id`),
    KEY `idx_sensor_device_id` (`device_id`),
    KEY `idx_sensor_element_code` (`element_code`),
    KEY `idx_sensor_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器对象';
CREATE TABLE IF NOT EXISTS `biz_sensor_reading` (
    `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '读数行',
    `sensor_id`   bigint        NOT NULL               COMMENT '传感器对象 ID',
    `record_time` datetime      NOT NULL               COMMENT '记录时间',
    `raw_value`   decimal(18,4) DEFAULT NULL           COMMENT '原始值',
    `value`       decimal(18,4) DEFAULT NULL           COMMENT '工程量',
    `quality`     tinyint       DEFAULT NULL           COMMENT '0 正常 / 1 可疑 / 2 无效',
    `is_alarm`    bit(1)        NOT NULL DEFAULT b'0'  COMMENT '是否报警值',
    `ext_json`    json          DEFAULT NULL           COMMENT '扩展字段',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sensor_time` (`sensor_id`, `record_time`),
    KEY `idx_reading_time` (`record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器观测值窄表';
-- changeset jdrk:v14-5
-- comment 现有通道迁移为传感器对象（幂等）
INSERT INTO `biz_sensor` (`name`, `group_id`, `port_id`, `device_id`, `channel_no`, `analog_no`, `element_code`, `unit`, `range_min`, `range_max`, `coefficient`, `offset_value`, `enabled`, `create_user`, `create_time`, `deleted`)
SELECT CONCAT(d.`name`, '-CH', c.`channel_no`, '-A', c.`analog_no`), d.`group_id`, d.`port_id`, d.`id`, c.`channel_no`, c.`analog_no`,
       COALESCE(NULLIF(c.`element_type`, ''), CONCAT('CH', c.`channel_no`, '_', c.`analog_no`)), c.`unit`, c.`min_value`, c.`max_value`, c.`coefficient`, c.`offset_value`, c.`enabled`, 0, NOW(), 0
FROM `biz_channel` c JOIN `biz_device` d ON d.`id` = c.`device_id`
WHERE c.`deleted` = 0 AND d.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `biz_sensor` s WHERE s.`device_id` = d.`id` AND s.`channel_no` = c.`channel_no` AND s.`analog_no` = c.`analog_no` AND s.`deleted` = 0);
-- changeset jdrk:v14-6
-- comment 地图配置默认项
INSERT INTO `biz_sys_config` (`category`, `name`, `code`, `value`, `default_value`, `description`, `create_user`, `create_time`, `deleted`)
SELECT 'MAP', '高德地图开关', 'AMAP_ENABLED', '0', '0', '0 关闭/降级；1 启用', 0, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `biz_sys_config` WHERE `code` = 'AMAP_ENABLED' AND `deleted` = 0);
INSERT INTO `biz_sys_config` (`category`, `name`, `code`, `value`, `default_value`, `description`, `create_user`, `create_time`, `deleted`)
SELECT 'MAP', '高德 JS Key', 'AMAP_JS_KEY', '', '', '运行时下发，不写入前端包', 0, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `biz_sys_config` WHERE `code` = 'AMAP_JS_KEY' AND `deleted` = 0);
INSERT INTO `biz_sys_config` (`category`, `name`, `code`, `value`, `default_value`, `description`, `create_user`, `create_time`, `deleted`)
SELECT 'MAP', '高德安全密钥', 'AMAP_SECURITY_CODE', '', '', '运行时下发，不写入前端包', 0, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `biz_sys_config` WHERE `code` = 'AMAP_SECURITY_CODE' AND `deleted` = 0);
-- changeset jdrk:v14-7
-- comment V1.4 分组树/传感器/地图菜单与权限
INSERT INTO `sys_menu` (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(4300, '设备分组', 4000, 2, '/env/device-group', 'EnvDeviceGroup', 'env/device-group/index', NULL, 'folder', b'0', b'0', b'0', NULL, 1, 1, 1, NOW()),
(4301, '列表', 4300, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'device:group:list', 1, 1, 1, NOW()),
(4302, '新增', 4300, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'device:group:create', 2, 1, 1, NOW()),
(4303, '修改', 4300, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'device:group:update', 3, 1, 1, NOW()),
(4304, '删除', 4300, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'device:group:delete', 4, 1, 1, NOW()),
(4310, '传感器对象', 4000, 2, '/env/sensor', 'EnvSensor', 'env/sensor/index', NULL, 'bulb', b'0', b'0', b'0', NULL, 2, 1, 1, NOW()),
(4311, '列表', 4310, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:sensor:list', 1, 1, 1, NOW()),
(4312, '新增', 4310, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:sensor:create', 2, 1, 1, NOW()),
(4313, '修改', 4310, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:sensor:update', 3, 1, 1, NOW()),
(4314, '删除', 4310, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'monitor:sensor:delete', 4, 1, 1, NOW()),
(4320, '设备地图', 4000, 2, '/env/device-map', 'EnvDeviceMap', 'env/device-map/index', NULL, 'location', b'0', b'0', b'0', NULL, 3, 1, 1, NOW()),
(4321, '查看', 4320, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'device:map:view', 1, 1, 1, NOW()),
(4322, '位置修改', 4320, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'device:location:update', 2, 1, 1, NOW());
-- changeset jdrk:v14-8
-- comment 将 V1.4 菜单授予已有环境监测角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT r.`role_id`, m.`id` FROM `sys_role_menu` r
JOIN `sys_menu` m ON m.`id` IN (4300,4301,4302,4303,4304,4310,4311,4312,4313,4314,4320,4321,4322)
WHERE r.`menu_id` = 4000;
-- changeset jdrk:v14-9
-- comment 页面清理：下架租户/能力开放/开发工具，任务调度收进系统管理
UPDATE `sys_menu` SET `deleted` = `id`, `update_time` = NOW()
WHERE `deleted` = 0 AND (`id` BETWEEN 3000 AND 3025 OR `id` BETWEEN 7000 AND 7018 OR `id` BETWEEN 9000 AND 9014);
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (SELECT `id` FROM `sys_menu` WHERE `deleted` <> 0);
UPDATE `sys_menu` SET `parent_id` = 1000, `path` = '/system/schedule', `sort` = 50, `update_time` = NOW()
WHERE `id` = 8000 AND `deleted` = 0;
UPDATE `sys_menu` SET `path` = REPLACE(`path`, '/schedule', '/system/schedule'), `update_time` = NOW()
WHERE `parent_id` = 8000 AND `deleted` = 0 AND `path` LIKE '/schedule%';
-- changeset jdrk:v14-10
-- comment 角色设备数据范围（授权分组集合）
CREATE TABLE IF NOT EXISTS `biz_role_group_scope` (
    `id`         bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `role_id`    bigint NOT NULL COMMENT '角色 ID',
    `group_id`   bigint NOT NULL COMMENT '授权分组 ID',
    `create_user` bigint NOT NULL DEFAULT 0 COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_group` (`role_id`, `group_id`),
    KEY `idx_scope_role` (`role_id`),
    KEY `idx_scope_group` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色授权分组范围';
