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

package top.continew.admin.monitor.runtime.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizPortDO;
import top.continew.admin.monitor.runtime.model.ImportRowErrorResp;
import top.continew.admin.monitor.runtime.model.MigrationDetectResp;
import top.continew.admin.monitor.runtime.model.MigrationImportResp;
import top.continew.starter.core.util.validation.CheckUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 迁移向导的「探测原库 + 映射设备」两步（FR-IO-06 第 1/2 步、FR-MIG-03）
 *
 * <p>与历史数据迁移/一致性校验分离：探测只读源库元数据，设备映射只写本系统设备表，
 * 便于单独排障与复用（NFR-MAINT-03）。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationProbeService {

    /** DR-03 标准表 */
    private static final List<String> DR03_TABLES = List.of("tbhistory", "tbalarm", "tbnoticelog",
        "tbsyslog");

    /** 疑似设备/通道配置表的表名关键字 */
    private static final List<String> DEVICE_HINTS = List.of("device", "dev", "sensor", "channel",
        "node", "port");

    private final MigrationProperties properties;
    private final BizDeviceMapper deviceMapper;
    private final BizPortMapper portMapper;

    /**
     * 打开源库连接（校验连接参数并探活一次）
     *
     * @param spec 连接参数
     * @return 源库客户端
     */
    public MigrationSourceClient client(MigrationSpec spec) {
        CheckUtils.throwIf(spec == null || spec.host() == null || spec.host().isBlank(),
            "源库地址不能为空");
        CheckUtils.throwIf(spec.database() == null || spec.database().isBlank(), "源库名不能为空");
        CheckUtils.throwIf(spec.username() == null || spec.username().isBlank(), "源库用户名不能为空");
        try {
            MigrationSourceClient source = new MigrationSourceClient(spec, properties
                .getConnectTimeoutSeconds());
            source.database();
            return source;
        } catch (Exception e) {
            throw new IllegalStateException("连接源库失败：" + e.getMessage(), e);
        }
    }

    /**
     * 检测原库（FR-IO-06 第 1 步）
     *
     * @param spec 连接参数
     * @return 探测结果
     */
    public MigrationDetectResp detect(MigrationSpec spec) {
        MigrationDetectResp resp = new MigrationDetectResp();
        try {
            MigrationSourceClient source = this.client(spec);
            resp.setConnected(true);
            resp.setDatabase(source.database());
            List<String> tables = source.listTables();
            resp.setTableCount(tables.size());
            // 审计修复：行数与列名各用一条连接批量取回，避免「每张表各开一次连接」
            // （源库表多时建连开销占大头）
            Map<String, Long> counts = source.countAll(tables);
            Map<String, List<String>> columns = source.columnsAll();
            for (String table : tables) {
                MigrationDetectResp.TableRow row = new MigrationDetectResp.TableRow();
                row.setName(table);
                row.setRows(counts.getOrDefault(table, 0L));
                row.setDr03(DR03_TABLES.contains(table.toLowerCase(Locale.ROOT)));
                row.setDeviceTable(row.getDr03() || this.looksLikeDeviceTable(columns, table));
                if (Boolean.TRUE.equals(row.getDr03())) {
                    resp.getDr03Tables().add(table);
                }
                if (Boolean.TRUE.equals(row.getDeviceTable())
                    && !Boolean.TRUE.equals(row.getDr03())) {
                    resp.getDeviceTables().add(table);
                }
                resp.getTables().add(row);
            }
            log.info("源库探测完成：{} 张表，DR-03 表 {} 张，疑似设备表 {}", tables.size(), resp
                .getDr03Tables().size(), resp.getDeviceTables().size());
            return resp;
        } catch (Exception e) {
            resp.setConnected(false);
            resp.setErrorMsg(e.getMessage());
            log.warn("源库探测失败：{}", e.getMessage());
            return resp;
        }
    }

    /**
     * 映射设备（FR-IO-06 第 2 步、FR-MIG-03 配置迁移）
     *
     * @param spec       连接参数
     * @param table      源设备表
     * @param portId     目标端口
     * @param protocol   目标协议
     * @param nameColumn 源名称列
     * @param addrColumn 源地址列
     * @param dryRun     是否仅预演
     * @return 导入结果
     */
    public MigrationImportResp importDevices(MigrationSpec spec, String table, Long portId,
        String protocol, String nameColumn, String addrColumn, boolean dryRun) {
        final long startedAt = System.currentTimeMillis();
        MigrationImportResp resp = new MigrationImportResp();
        resp.setType("DEVICE");
        resp.setDryRun(dryRun);
        List<ImportRowErrorResp> errors = new ArrayList<>();
        BizPortDO port = portMapper.selectById(portId);
        CheckUtils.throwIf(port == null, "目标端口不存在：{}", portId);
        String name = nameColumn == null || nameColumn.isBlank() ? "DeviceName" : nameColumn.trim();
        String addr = addrColumn == null || addrColumn.isBlank() ? "DeviceAddr" : addrColumn.trim();
        MigrationSourceClient source = this.client(spec);
        CheckUtils.throwIf(!source.hasTable(table), "源表不存在：{}", table);
        List<String> columns = source.columns(table);
        CheckUtils.throwIf(!columns.contains(name), "源表缺少名称列：{}", name);
        CheckUtils.throwIf(!columns.contains(addr), "源表缺少地址列：{}", addr);
        List<Map<String, Object>> rows = source.jdbc()
            .queryForList("SELECT " + MigrationSourceClient
                .escape(name) + " AS name, " + MigrationSourceClient.escape(addr)
                + " AS addr FROM " + MigrationSourceClient.escape(table));
        CheckUtils.throwIf(rows.size() > MigrationSourceClient.MAX_ROWS, "源表行数超过上限 {}，请先分段迁移",
            MigrationSourceClient.MAX_ROWS);
        resp.setScanned(rows.size());
        int imported = 0;
        int skipped = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            int line = i + 1;
            try {
                String deviceName = row.get("name") == null ? null : String.valueOf(row.get("name"))
                    .trim();
                CheckUtils.throwIf(deviceName == null || deviceName.isBlank(), "第 {} 行设备名为空", line);
                int addrValue =
                    row.get("addr") == null ? 1 : (int) Double.parseDouble(String.valueOf(row
                        .get("addr")));
                BizDeviceDO existing =
                    deviceMapper.lambdaQuery().eq(BizDeviceDO::getName, deviceName)
                        .last("LIMIT 1")
                        .one();
                BizDeviceDO entity = new BizDeviceDO();
                if (existing != null) {
                    entity.setId(existing.getId());
                }
                entity.setPortId(port.getId());
                entity.setName(deviceName);
                entity.setSlaveAddr(addrValue);
                entity.setProtocol(protocol == null || protocol.isBlank() ? "RS_MODBUS" : protocol
                    .trim());
                entity.setPriority(existing == null ? 0 : existing.getPriority());
                entity.setRelayRegBase(existing == null ? 696 : existing.getRelayRegBase());
                entity.setRelayCount(existing == null ? 8 : existing.getRelayCount());
                entity.setEnabled(true);
                entity.setDescription("由迁移向导从 " + table + " 导入");
                if (!dryRun) {
                    if (existing == null) {
                        deviceMapper.insert(entity);
                    } else {
                        deviceMapper.updateById(entity);
                    }
                }
                imported++;
            } catch (Exception e) {
                errors.add(new ImportRowErrorResp(line, e.getMessage()));
            }
        }
        resp.setImported(imported);
        resp.setSkipped(skipped);
        resp.setFailed(errors.size());
        resp.setErrors(errors.size() > 200 ? errors.subList(0, 200) : errors);
        resp.setDurationMs(System.currentTimeMillis() - startedAt);
        log.info("设备映射完成：源 {} 行，写入 {}，失败 {}", rows.size(), imported, errors.size());
        return resp;
    }

    /**
     * 是否疑似设备表（表名关键字或含 DeviceName/DeviceAddr/Node 列）
     *
     * @param columns 已批量取回的「表名 -> 列名」（探测阶段取一次，避免逐表建连）
     * @param table   表名
     * @return 疑似设备表返回 true
     */
    private boolean looksLikeDeviceTable(Map<String, List<String>> columns, String table) {
        String lower = table.toLowerCase(Locale.ROOT);
        for (String hint : DEVICE_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        try {
            List<String> tableColumns = columns.getOrDefault(table, List.of());
            for (String column : tableColumns) {
                String name = column.toLowerCase(Locale.ROOT);
                if (name.contains("devicename") || name.contains("deviceaddr")
                    || name.contains("node")) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("读取列名失败：{}", e.getMessage());
        }
        return false;
    }
}
