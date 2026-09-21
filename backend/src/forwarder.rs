//! MySQL 回传：批量幂等写入（MySQL 8.4 行别名语法）。

use crate::config::MysqlTarget;
use crate::error::{AppError, Result};
use crate::store::OutboxBatch;
use mysql::prelude::Queryable;
use mysql::{Opts, Pool, Value};

pub struct MysqlSink {
    pub target: String,
    table: String,
    url: String,
    pool: Option<Pool>,
}

impl MysqlSink {
    pub fn new(target: &MysqlTarget) -> Self {
        Self {
            target: target.name.clone(),
            table: target.table.clone(),
            url: target.url.clone(),
            pool: None,
        }
    }

    fn ensure_pool(&mut self) -> Result<&Pool> {
        if self.pool.is_none() {
            let opts = Opts::from_url(&self.url)
                .map_err(|e| AppError::Other(format!("MySQL 连接串非法: {e}")))?;
            let pool = Pool::new(opts).map_err(|e| AppError::Other(format!("MySQL 连接池失败: {e}")))?;
            self.pool = Some(pool);
        }
        Ok(self.pool.as_ref().expect("pool"))
    }

    pub fn reset(&mut self) {
        self.pool = None;
    }

    /// 连通性测试。
    pub fn test(&mut self) -> Result<String> {
        let pool = self.ensure_pool()?;
        let mut conn = pool.get_conn().map_err(|e| AppError::Other(format!("MySQL 连接失败: {e}")))?;
        let one: Option<u8> = conn.query_first("SELECT 1").map_err(|e| AppError::Other(e.to_string()))?;
        Ok(format!("SELECT 1 -> {:?}", one))
    }

    /// 批量 UPSERT。返回写入行数。
    pub fn send_batch(&mut self, batch: &OutboxBatch) -> Result<usize> {
        if batch.samples.is_empty() {
            return Ok(0);
        }
        let pool = self.ensure_pool()?;
        let mut conn = pool.get_conn().map_err(|e| AppError::Other(format!("MySQL 连接失败: {e}")))?;

        let n = batch.samples.len();
        let mut sql = format!(
            "INSERT INTO {} (record_time, device_sn, channel_no, value, raw_value, quality, rtt_ms) VALUES ",
            sanitize_ident(&self.table)
        );
        for i in 0..n {
            if i > 0 {
                sql.push(',');
            }
            sql.push_str("(?,?,?,?,?,?,?)");
        }
        // MySQL 8.0.19+ 行别名，替代已弃用的 VALUES(col)
        sql.push_str(
            " AS new ON DUPLICATE KEY UPDATE value=new.value, raw_value=new.raw_value, quality=new.quality, rtt_ms=new.rtt_ms",
        );

        let mut params: Vec<Value> = Vec::with_capacity(n * 7);
        for s in &batch.samples {
            params.push(Value::Bytes(
                s.record_time.format("%Y-%m-%d %H:%M:%S%.3f").to_string().into_bytes(),
            ));
            params.push(Value::Bytes(s.device_sn.clone().into_bytes()));
            params.push(Value::Int(s.channel_no as i64));
            params.push(match s.value {
                Some(v) => Value::Double(v),
                None => Value::NULL,
            });
            params.push(match s.raw_value {
                Some(v) => Value::Int(v),
                None => Value::NULL,
            });
            params.push(Value::Bytes(s.quality.as_str().as_bytes().to_vec()));
            params.push(match s.rtt_ms {
                Some(v) => Value::Int(v as i64),
                None => Value::NULL,
            });
        }

        conn.exec_drop(sql, mysql::Params::Positional(params))
            .map_err(|e| AppError::Other(format!("MySQL 写入失败: {e}")))?;
        Ok(n)
    }
}

/// 只允许字母数字下划线，避免表名注入。
fn sanitize_ident(name: &str) -> String {
    if name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') && !name.is_empty() {
        name.to_string()
    } else {
        "tb_sensor_data".to_string()
    }
}
