-- ============================================================
-- 本地上位机缓冲库（SQLite，WAL）—— JDRK 水质监控平台
-- ============================================================

CREATE TABLE IF NOT EXISTS meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS channel_config (
  channel_no   INTEGER PRIMARY KEY,
  name         TEXT NOT NULL,
  unit         TEXT NOT NULL DEFAULT '',
  sensor_model TEXT NOT NULL DEFAULT '',
  data_type    TEXT NOT NULL DEFAULT 'ai1',
  coef_a       REAL NOT NULL DEFAULT 1.0,
  coef_b       REAL NOT NULL DEFAULT 0.0,
  decimals     INTEGER NOT NULL DEFAULT 2,
  upper_limit  REAL,
  lower_limit  REAL,
  enabled      INTEGER NOT NULL DEFAULT 1,
  slave_addr   INTEGER NOT NULL DEFAULT 1,
  updated_at   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ts_data (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  record_time  TEXT NOT NULL,
  device_sn    TEXT NOT NULL,
  channel_no   INTEGER NOT NULL,
  channel_name TEXT NOT NULL DEFAULT '',
  unit         TEXT NOT NULL DEFAULT '',
  value        REAL,
  raw_value    INTEGER,
  quality      TEXT NOT NULL DEFAULT 'ok',
  rtt_ms       INTEGER,
  created_at   TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (record_time, device_sn, channel_no)
);

CREATE INDEX IF NOT EXISTS idx_ts_dev_ch_time ON ts_data (device_sn, channel_no, record_time);
CREATE INDEX IF NOT EXISTS idx_ts_time ON ts_data (record_time);

CREATE TABLE IF NOT EXISTS alarm (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  device_sn    TEXT NOT NULL,
  channel_no   INTEGER NOT NULL,
  channel_name TEXT NOT NULL DEFAULT '',
  alarm_type   TEXT NOT NULL,
  value        REAL,
  threshold    REAL,
  raised_at    TEXT NOT NULL,
  cleared_at   TEXT,
  created_at   TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alarm_dev_ch ON alarm (device_sn, channel_no, raised_at);

CREATE TABLE IF NOT EXISTS forward_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  target      TEXT NOT NULL,
  batch_count INTEGER NOT NULL DEFAULT 0,
  ok_count    INTEGER NOT NULL DEFAULT 0,
  fail_count  INTEGER NOT NULL DEFAULT 0,
  elapsed_ms  INTEGER,
  message     TEXT NOT NULL DEFAULT '',
  created_at  TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS outbox (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  target     TEXT NOT NULL,
  payload    TEXT NOT NULL,
  rows       INTEGER NOT NULL DEFAULT 0,
  attempts   INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  created_at TEXT NOT NULL,
  sent_at    TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbox_target ON outbox (target, sent_at, id);
