/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.monitor.runtime.report;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mbj.jdcompatiblemonitor.report.ReportItem;
import com.mbj.jdcompatiblemonitor.report.SqlDialect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库上报（FR-RPT-03）。
 *
 * <p>按 DR-03 的仁科兼容模型写入历史表：同一「设备地址 + 节点 + 记录时间」的模拟量1/模拟量2
 * 合并为一行（{@code Tem} / {@code Hum}），保证第三方取数程序无需改造。</p>
 *
 * <p>连接参数：`url`、`username`、`password`、`table`（默认 tbhistory）、
 * `coordinateType`（默认 3=GPS）、`source`（默认 1=本平台）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
public class JdbcReportTransport implements ReportTransport {

    /** DR-03 目标表列顺序 */
    private static final List<String> COLUMNS = List.of("ID", "DeviceName", "DeviceAddr", "NodeId",
        "Hum", "Tem", "CoordinateType", "Lng", "Lat", "RecordTime", "IsAlarmData", "Source");

    @Override
    public String type() {
        return "DB";
    }

    /**
     * MySQL 连接串补上 rewriteBatchedStatements（NFR-PERF-06）
     *
     * <p>上报走的是 {@code addBatch/executeBatch}，而 MySQL Connector/J 默认会把批量拆成逐条发送：
     * 实测同一条全量规则下，目标库 URL 缺这个参数时只有 1,111 条/秒，加上之后才能压到采集端上限。
     * 只对 MySQL URL 且用户没显式配置时补，不做静默改写。</p>
     *
     * @param url 目标库连接串
     * @return 补好参数的连接串
     */
    private String batchFriendlyUrl(String url) {
        if (url == null || !url.startsWith("jdbc:mysql")
            || url.contains("rewriteBatchedStatements")) {
            return url;
        }
        String patched = url + (url.contains("?") ? "&" : "?") + "rewriteBatchedStatements=true";
        log.info("数据库上报目标未开启批量重写，已自动追加 rewriteBatchedStatements=true 以提升吞吐");
        return patched;
    }

    @Override
    public void send(ReportTransmission transmission) throws Exception {
        ReportTargetSpec target = transmission.target();
        JSONObject config = JSONUtil.parseObj(target.configJson());
        String url = this.batchFriendlyUrl(config.getStr("url"));
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("数据库目标未配置 url");
        }
        SqlDialect dialect = SqlDialect.parse(target.dbType());
        this.ensureDriver(dialect);
        String table = config.getStr("table", "tbhistory");
        int coordinateType = config.getInt("coordinateType", 3);
        int source = config.getInt("source", 1);

        List<Map<String, Object>> rows = this.toRows(transmission, coordinateType, source);
        if (rows.isEmpty()) {
            return;
        }
        String sql = dialect.insertSql(table, COLUMNS);
        try (Connection connection = DriverManager.getConnection(url, config.getStr("username"),
            config.getStr("password"))) {
            // 批次必须原子落库：JDBC 默认 autoCommit=true，部分行提交后失败会被整批重试，
            // 而重试会重新生成 ID（见 toRows），于是已提交的行被重复写入（DR-03 出现重复历史行）。
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Map<String, Object> row : rows) {
                    int index = 1;
                    for (String column : COLUMNS) {
                        statement.setObject(index++, row.get(column));
                    }
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (Exception rollbackError) {
                    log.warn("数据库上报回滚失败：{}", rollbackError.getMessage());
                }
                throw e;
            }
        }
    }

    /**
     * 按 DR-03 模型合并数据行
     *
     * @param transmission   传输输入
     * @param coordinateType 坐标类型
     * @param source         数据来源
     * @return 待插入的行
     */
    private List<Map<String, Object>> toRows(ReportTransmission transmission, int coordinateType,
        int source) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (ReportItem item : transmission.batch().items()) {
            String key = item.deviceAddr() + ":" + item.nodeId() + ":" + item.collectTime();
            Map<String, Object> row = merged.computeIfAbsent(key, k -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("ID", IdUtil.fastSimpleUUID());
                created.put("DeviceName", item.deviceName());
                created.put("DeviceAddr", item.deviceAddr());
                created.put("NodeId", item.nodeId());
                created.put("Hum", null);
                created.put("Tem", null);
                created.put("CoordinateType", coordinateType);
                created.put("Lng", item.lng());
                created.put("Lat", item.lat());
                created.put("RecordTime", toTimestamp(item.collectTime()));
                created.put("IsAlarmData", item.alarm() ? 1 : 0);
                created.put("Source", source);
                return created;
            });
            // 0x0000 为模拟量2 -> Hum；0x0001 为模拟量1 -> Tem（与仁科模型一致）
            if (item.analogNo() == 2) {
                row.put("Hum", item.value());
            } else if (item.analogNo() == 1) {
                row.put("Tem", item.value());
            }
            if (item.alarm()) {
                row.put("IsAlarmData", 1);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 确认驱动可用并注册
     *
     * @param dialect 方言
     */
    private void ensureDriver(SqlDialect dialect) {
        try {
            Class.forName(dialect.driverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("目标库驱动未提供：" + dialect.driverClass()
                + "（请将对应 JDBC 驱动放入运行目录）");
        }
    }

    private Timestamp toTimestamp(long millis) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis),
            ZoneId.systemDefault()));
    }
}
