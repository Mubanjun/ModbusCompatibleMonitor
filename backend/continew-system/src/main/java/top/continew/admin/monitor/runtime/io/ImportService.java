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

package top.continew.admin.monitor.runtime.io;

import cn.hutool.core.date.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.monitor.mapper.BizChannelMapper;
import top.continew.admin.monitor.mapper.BizDataLatestMapper;
import top.continew.admin.monitor.mapper.BizDataMapper;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.mapper.BizSysConfigMapper;
import top.continew.admin.monitor.model.entity.BizChannelDO;
import top.continew.admin.monitor.model.entity.BizDataDO;
import top.continew.admin.monitor.model.entity.BizDataLatestDO;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizPortDO;
import top.continew.admin.monitor.model.entity.BizSysConfigDO;
import top.continew.admin.monitor.runtime.model.ImportPreviewResp;
import top.continew.admin.monitor.runtime.model.ImportReportResp;
import top.continew.admin.monitor.runtime.model.ImportRowErrorResp;
import top.continew.admin.monitor.service.BizDataService;
import top.continew.starter.core.util.validation.CheckUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 数据导入（FR-IO-01：历史数据、设备清单、配置；Excel/CSV；导入前校验、错误可定位）。
 *
 * <p>统一流程：解析表格 → 校验必需表头 → 逐行校验并写入 → 汇总报告。任何一行失败都不会
 * 中断整体导入，失败行的**文件行号**与原因会出现在报告的 errors 中。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    /** 报告里最多保留的错误条数 */
    private static final int MAX_ERRORS = 200;

    /** 预览行数 */
    private static final int PREVIEW_ROWS = 20;

    /** 历史数据导入的批量落库条数（审计修复：原来是逐行 count + insert + 实时值读写） */
    private static final int HISTORY_BATCH_SIZE = 500;

    /** 允许的协议 */
    private static final Set<String> PROTOCOLS = Set.of("MODBUS_RTU", "RS_MODBUS", "MODBUS_TCP",
        "MODBUS_ASCII");

    private static final List<String> CONFIG_HEADERS = List.of("category", "code", "value");
    private static final List<String> DEVICE_HEADERS =
        List.of("name", "portName", "slaveAddr", "protocol");
    private static final List<String> HISTORY_HEADERS = List.of("deviceName", "nodeId", "analogNo",
        "dataValue", "recordTime");

    private final BizSysConfigMapper sysConfigMapper;
    private final BizDeviceMapper deviceMapper;
    private final BizPortMapper portMapper;
    private final BizChannelMapper channelMapper;
    private final BizDataMapper dataMapper;
    private final BizDataLatestMapper dataLatestMapper;
    private final BizDataService bizDataService;

    /**
     * 导入前校验：表头、行数与前若干行样例
     *
     * @param type 导入类型
     * @param file 上传文件
     * @return 预览结果
     */
    public ImportPreviewResp preview(ImportType type, MultipartFile file) {
        TabularData data = this.parse(file);
        List<String> required = requiredHeaders(type);
        ImportPreviewResp resp = new ImportPreviewResp();
        resp.setType(type.name());
        resp.setHeaders(data.headers());
        resp.setTotal(data.rows().size());
        List<String> missing = new ArrayList<>();
        for (String header : required) {
            if (!data.headers().contains(header)) {
                missing.add(header);
            }
        }
        resp.setMissingHeaders(missing);
        resp.setHeadersValid(missing.isEmpty());
        for (int i = 0; i < Math.min(PREVIEW_ROWS, data.rows().size()); i++) {
            resp.getSampleRows().add(this.toText(data.headers(), data.rows().get(i)));
        }
        return resp;
    }

    /**
     * 执行导入
     *
     * @param type    导入类型
     * @param file    上传文件
     * @param dryRun  是否只校验不写入
     * @return 导入报告
     */
    public ImportReportResp execute(ImportType type, MultipartFile file, boolean dryRun) {
        long start = System.currentTimeMillis();
        TabularData data = this.parse(file);
        List<String> missing = new ArrayList<>();
        for (String header : requiredHeaders(type)) {
            if (!data.headers().contains(header)) {
                missing.add(header);
            }
        }
        CheckUtils.throwIf(!missing.isEmpty(), "缺少必需列：{}", String.join(",", missing));
        ImportReportResp report = new ImportReportResp();
        report.setType(type.name());
        report.setDryRun(dryRun);
        report.setTotal(data.rows().size());
        List<ImportRowErrorResp> errors = new ArrayList<>();
        int success = 0;
        int skipped = 0;
        switch (type) {
            case CONFIG -> {
                int[] result = this.importConfig(data, dryRun, errors);
                success = result[0];
                skipped = result[1];
            }
            case DEVICE -> {
                int[] result = this.importDevice(data, dryRun, errors);
                success = result[0];
                skipped = result[1];
            }
            default -> {
                int[] result = this.importHistory(data, dryRun, errors);
                success = result[0];
                skipped = result[1];
            }
        }
        report.setSuccess(success);
        report.setSkipped(skipped);
        report.setFailed(data.rows().size() - success - skipped);
        report.setErrors(errors.size() > MAX_ERRORS ? errors.subList(0, MAX_ERRORS) : errors);
        report.setDurationMs(System.currentTimeMillis() - start);
        log.info("导入完成：type={} total={} success={} skipped={} failed={} dryRun={}", type, report
            .getTotal(), success, skipped, report.getFailed(), dryRun);
        return report;
    }

    private int[] importConfig(TabularData data, boolean dryRun, List<ImportRowErrorResp> errors) {
        int success = 0;
        int skipped = 0;
        for (int i = 0; i < data.rows().size(); i++) {
            int line = i + 2;
            Map<String, String> row = data.rows().get(i);
            try {
                String category = this.require(row, "category", line);
                String code = this.require(row, "code", line);
                String value = row.get("value");
                BizSysConfigDO existing =
                    sysConfigMapper.lambdaQuery().eq(BizSysConfigDO::getCategory,
                        category).eq(BizSysConfigDO::getCode, code).one();
                if (existing == null) {
                    BizSysConfigDO entity = new BizSysConfigDO();
                    entity.setCategory(category);
                    entity.setName(this.text(row, "name", code));
                    entity.setCode(code);
                    entity.setValue(value);
                    entity.setDefaultValue(this.text(row, "defaultValue", value));
                    entity.setDescription(row.get("description"));
                    if (!dryRun) {
                        sysConfigMapper.insert(entity);
                    }
                    success++;
                } else {
                    BizSysConfigDO update = new BizSysConfigDO();
                    update.setId(existing.getId());
                    update.setValue(value);
                    update.setDescription(row.get("description"));
                    if (!dryRun) {
                        sysConfigMapper.updateById(update);
                    }
                    success++;
                }
            } catch (Exception e) {
                errors.add(new ImportRowErrorResp(line, e.getMessage()));
            }
        }
        return new int[] {success, skipped};
    }

    private int[] importDevice(TabularData data, boolean dryRun, List<ImportRowErrorResp> errors) {
        int success = 0;
        int skipped = 0;
        for (int i = 0; i < data.rows().size(); i++) {
            int line = i + 2;
            Map<String, String> row = data.rows().get(i);
            try {
                String name = this.require(row, "name", line);
                String portName = this.require(row, "portName", line);
                int slaveAddr = this.toInt(row, "slaveAddr", line, 1, 247);
                String protocol = this.require(row, "protocol", line).toUpperCase(Locale.ROOT);
                CheckUtils.throwIf(!PROTOCOLS.contains(protocol), "不支持的协议：{}", protocol);
                BizPortDO port = portMapper.lambdaQuery().eq(BizPortDO::getName, portName).one();
                CheckUtils.throwIf(port == null, "端口不存在：{}", portName);
                BizDeviceDO existing =
                    deviceMapper.lambdaQuery().eq(BizDeviceDO::getName, name).one();
                BizDeviceDO entity = new BizDeviceDO();
                if (existing != null) {
                    entity.setId(existing.getId());
                }
                entity.setPortId(port.getId());
                entity.setName(name);
                entity.setSlaveAddr(slaveAddr);
                entity.setProtocol(protocol);
                entity.setModel(row.get("model"));
                entity.setGroupName(row.get("groupName"));
                entity.setLocation(row.get("location"));
                entity.setIntervalMs(this.optionalInt(row, "intervalMs"));
                entity.setTimeoutMs(this.optionalInt(row, "timeoutMs"));
                entity.setPriority(this.optionalInt(row, "priority"));
                entity.setRelayRegBase(this.optionalInt(row, "relayRegBase"));
                entity.setRelayCount(this.optionalInt(row, "relayCount"));
                entity.setEnabled(!"0".equals(this.text(row, "enabled", "1")));
                entity.setDescription(row.get("description"));
                if (!dryRun) {
                    if (existing == null) {
                        deviceMapper.insert(entity);
                    } else {
                        deviceMapper.updateById(entity);
                    }
                }
                success++;
            } catch (Exception e) {
                errors.add(new ImportRowErrorResp(line, e.getMessage()));
            }
        }
        return new int[] {success, skipped};
    }

    private int[] importHistory(TabularData data, boolean dryRun, List<ImportRowErrorResp> errors) {
        int success = 0;
        int skipped = 0;
        Map<String, BizDeviceDO> deviceCache = new HashMap<>();
        Map<String, BizChannelDO> channelCache = new HashMap<>();
        // 审计修复：整批收集后批量幂等落库（原实现每行一次 count + 一次 insert + 一次实时值读写，
        // 5000 行实测 23.5 秒、约 2 万次 SQL；改为每 500 行一批后降到秒级）
        List<BizDataDO> pending = new ArrayList<>(Math.min(data.rows().size(), HISTORY_BATCH_SIZE));
        Map<String, BizDataDO> latestCandidates = new LinkedHashMap<>();
        for (int i = 0; i < data.rows().size(); i++) {
            int line = i + 2;
            Map<String, String> row = data.rows().get(i);
            try {
                String deviceName = this.require(row, "deviceName", line);
                BizDeviceDO device = deviceCache.get(deviceName);
                if (device == null) {
                    device = deviceMapper.lambdaQuery().eq(BizDeviceDO::getName, deviceName)
                        .last("LIMIT 1").one();
                    CheckUtils.throwIf(device == null, "设备不存在：{}", deviceName);
                    deviceCache.put(deviceName, device);
                }
                int nodeId = this.toInt(row, "nodeId", line, 1, 255);
                int analogNo = this.toInt(row, "analogNo", line, 1, 32);
                BigDecimal value = this.toDecimal(row, "dataValue", line);
                LocalDateTime recordTime = this.toTime(row, "recordTime", line);
                long sourceId = sourceId(device.getId(), nodeId, analogNo, recordTime);
                BizChannelDO channel = this.findChannel(device, nodeId, analogNo, channelCache);
                BizDataDO entity = new BizDataDO();
                entity.setDeviceId(device.getId());
                entity.setDeviceName(device.getName());
                entity.setDeviceAddr(device.getSlaveAddr());
                entity.setNodeId(nodeId);
                entity.setAnalogNo(analogNo);
                entity.setElementType(channel == null ? row.get("elementType") : channel
                    .getElementType());
                entity.setElementName(this.text(row, "elementName", channel == null ? null : channel
                    .getElementName()));
                entity.setUnit(this.text(row, "unit", channel == null ? null : channel.getUnit()));
                entity.setDataValue(value == null ? null : value.doubleValue());
                BigDecimal raw = this.optionalDecimal(row, "rawValue");
                entity.setRawValue(raw == null ? null : raw.doubleValue());
                entity.setAlarmFlag(this.optionalInt(row, "alarmFlag"));
                entity.setRecordTime(recordTime);
                entity.setSourceId(sourceId);
                pending.add(entity);
                this.collectLatest(latestCandidates, entity);
                if (pending.size() >= HISTORY_BATCH_SIZE) {
                    int[] flushed = this.flushHistory(pending, dryRun);
                    success += flushed[0];
                    skipped += flushed[1];
                }
            } catch (Exception e) {
                errors.add(new ImportRowErrorResp(line, e.getMessage()));
            }
        }
        if (!pending.isEmpty()) {
            int[] flushed = this.flushHistory(pending, dryRun);
            success += flushed[0];
            skipped += flushed[1];
        }
        if (!dryRun) {
            // 实时值按通道只刷一次（取时间最新的一行），避免逐行读写
            for (BizDataDO candidate : latestCandidates.values()) {
                this.upsertLatest(candidate);
            }
        }
        return new int[] {success, skipped};
    }

    /**
     * 批量落库一批历史数据（幂等），返回 [成功, 跳过]
     *
     * @param pending 待写入行（方法内会清空）
     * @param dryRun  是否只预演
     * @return 成功条数与跳过条数
     */
    private int[] flushHistory(List<BizDataDO> pending, boolean dryRun) {
        if (pending.isEmpty()) {
            return new int[] {0, 0};
        }
        List<BizDataDO> batch = new ArrayList<>(pending);
        pending.clear();
        int total = batch.size();
        List<Long> sourceIds = batch.stream().map(BizDataDO::getSourceId).filter(Objects::nonNull)
            .toList();
        if (dryRun) {
            Set<Long> existing = sourceIds.isEmpty() ? Set.of() : new HashSet<>(dataMapper
                .selectExistingSourceIds(sourceIds));
            return new int[] {total - existing.size(), existing.size()};
        }
        int inserted = bizDataService.saveIdempotent(batch);
        return new int[] {inserted, total - inserted};
    }

    /**
     * 收集每通道时间最新的一行（用于批量刷新实时值）
     *
     * @param latestCandidates 每通道最新一行
     * @param entity           本次样本
     */
    private void collectLatest(Map<String, BizDataDO> latestCandidates, BizDataDO entity) {
        String key = entity.getDeviceId() + ":" + entity.getNodeId() + ":" + entity.getAnalogNo();
        BizDataDO newest = latestCandidates.get(key);
        if (newest == null || newest.getRecordTime().isBefore(entity.getRecordTime())) {
            latestCandidates.put(key, entity);
        }
    }

    private void upsertLatest(BizDataDO data) {
        BizDataLatestDO latest = dataLatestMapper.lambdaQuery()
            .eq(BizDataLatestDO::getDeviceId, data
                .getDeviceId())
            .eq(BizDataLatestDO::getNodeId, data.getNodeId()).eq(BizDataLatestDO::getAnalogNo,
                data.getAnalogNo())
            .one();
        if (latest != null && latest.getCollectTime() != null
            && latest.getCollectTime().isAfter(data
                .getRecordTime())) {
            return;
        }
        BizDataLatestDO entity = latest == null ? new BizDataLatestDO() : new BizDataLatestDO();
        if (latest != null) {
            entity.setId(latest.getId());
        }
        entity.setDeviceId(data.getDeviceId());
        entity.setDeviceName(data.getDeviceName());
        entity.setDeviceAddr(data.getDeviceAddr());
        entity.setNodeId(data.getNodeId());
        entity.setAnalogNo(data.getAnalogNo());
        entity.setElementType(data.getElementType());
        entity.setElementName(data.getElementName());
        entity.setUnit(data.getUnit());
        entity.setDataValue(data.getDataValue());
        entity.setRawValue(data.getRawValue());
        entity.setDeviceStatus("ONLINE");
        entity.setQuality("GOOD");
        entity.setCollectTime(data.getRecordTime());
        if (latest == null) {
            dataLatestMapper.insert(entity);
        } else {
            dataLatestMapper.updateById(entity);
        }
    }

    private BizChannelDO findChannel(BizDeviceDO device, int nodeId, int analogNo,
        Map<String, BizChannelDO> cache) {
        String key = device.getId() + ":" + nodeId + ":" + analogNo;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        BizChannelDO channel = channelMapper.lambdaQuery()
            .eq(BizChannelDO::getDeviceId, device.getId())
            .eq(BizChannelDO::getChannelNo, nodeId).eq(BizChannelDO::getAnalogNo, analogNo).one();
        cache.put(key, channel);
        return channel;
    }

    private TabularData parse(MultipartFile file) {
        CheckUtils.throwIf(file == null || file.isEmpty(), "上传文件不能为空");
        try {
            return TabularReader.read(file.getOriginalFilename(), file.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("解析文件失败：" + e.getMessage(), e);
        }
    }

    private static List<String> requiredHeaders(ImportType type) {
        return switch (type) {
            case CONFIG -> CONFIG_HEADERS;
            case DEVICE -> DEVICE_HEADERS;
            default -> HISTORY_HEADERS;
        };
    }

    private String require(Map<String, String> row, String key, int line) {
        String value = row.get(key);
        CheckUtils.throwIf(value == null || value.isBlank(), "第 {} 行缺少 {}", line, key);
        return value.trim();
    }

    private String text(Map<String, String> row, String key, String defaultValue) {
        String value = row.get(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private Integer optionalInt(Map<String, String> row, String key) {
        String value = row.get(key);
        return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
    }

    private int toInt(Map<String, String> row, String key, int line, int min, int max) {
        String value = this.require(row, key, line);
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("第 " + line + " 行 " + key + " 不是整数：" + value);
        }
        CheckUtils.throwIf(parsed < min || parsed > max, "第 {} 行 {} 超出范围（{}~{}）", line, key, min,
            max);
        return parsed;
    }

    private BigDecimal toDecimal(Map<String, String> row, String key, int line) {
        String value = this.require(row, key, line);
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("第 " + line + " 行 " + key + " 不是数值：" + value);
        }
    }

    private BigDecimal optionalDecimal(Map<String, String> row, String key) {
        String value = row.get(key);
        return value == null || value.isBlank() ? null : new BigDecimal(value.trim());
    }

    private LocalDateTime toTime(Map<String, String> row, String key, int line) {
        String value = this.require(row, key, line);
        try {
            return DateUtil.parse(value.trim()).toLocalDateTime();
        } catch (Exception e) {
            throw new IllegalArgumentException("第 " + line + " 行 " + key + " 时间格式无法识别：" + value);
        }
    }

    private String toText(List<String> headers, Map<String, String> row) {
        StringBuilder builder = new StringBuilder();
        Set<String> used = new HashSet<>();
        for (String header : headers) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(header).append('=').append(row.get(header));
            used.add(header);
        }
        return builder.toString();
    }

    /**
     * 稳定来源标识（FR-IO-01 幂等：同一设备/通道/时刻重复导入不会产生重复历史）
     *
     * @param deviceId   设备 ID
     * @param nodeId     节点号
     * @param analogNo   模拟量序号
     * @param recordTime 记录时间
     * @return FNV-1a 64 位哈希（非负）
     */
    public static long sourceId(Long deviceId, Integer nodeId, Integer analogNo,
        LocalDateTime recordTime) {
        String key = deviceId + ":" + nodeId + ":" + analogNo + ":" + recordTime;
        long hash = 0xcbf29ce484222325L;
        for (byte current : key.getBytes(StandardCharsets.UTF_8)) {
            hash ^= current & 0xFF;
            hash *= 0x100000001b3L;
        }
        return hash & Long.MAX_VALUE;
    }
}
