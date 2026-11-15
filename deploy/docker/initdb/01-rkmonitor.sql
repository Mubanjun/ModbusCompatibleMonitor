-- 验收套件需要独立的仁科兼容目标库 rkmonitor（FR-RPT/FR-SYNC 用）
CREATE DATABASE IF NOT EXISTS rkmonitor CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON rkmonitor.* TO 'jdrk'@'%';
FLUSH PRIVILEGES;
