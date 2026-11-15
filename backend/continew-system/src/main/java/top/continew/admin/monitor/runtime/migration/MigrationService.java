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
import top.continew.admin.monitor.mapper.BizChannelMapper;
import top.continew.admin.monitor.mapper.BizDataLatestMapper;
import top.continew.admin.monitor.mapper.BizDataMapper;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizSysConfigMapper;
import top.continew.admin.monitor.model.entity.BizChannelDO;
import top.continew.admin.monitor.model.entity.BizDataDO;
import top.continew.admin.monitor.model.entity.BizDataLatestDO;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizSysConfigDO;
import top.continew.admin.monitor.runtime.io.ImportService;
import top.continew.admin.monitor.service.BizDataService;
import top.continew.admin.monitor.runtime.model.ImportRowErrorResp;
import top.continew.admin.monitor.runtime.model.MigrationImportResp;
import top.continew.admin.monitor.runtime.model.MigrationSampleResp;
import top.continew.admin.monitor.runtime.model.MigrationVerifyResp;
import top.continew.starter.core.util.validation.CheckUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 迁移向导与无损替换（FR-IO-06、FR-MIG-01~06）。
 *
 * <p>向导四步：检测原库 → 映射设备 → 导入历史 → 校验一致性；一致性校验可随时重跑并留下报告
 * （FR-MIG-06）。运行模式（OFF/COEXIST/SWITCHED）持久化在业务配置 SYSTEM.MIGRATION_MODE，
 * 供并存错峰与最终切换使用。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    /** 迁移模式配置项 */
    private static final String KEY_MODE = "MIGRATION_MODE";

    /** 源历史表 */
    private static final String SOURCE_HISTORY = "tbhistory";

    /** 数值一致性容差 */
    private static final double TOLERANCE = 1e-6;

    private final MigrationProperties properties;
    private final BizDeviceMapper deviceMapper;
    private final BizChannelMapper channelMapper;
    private final BizDataMapper dataMapper;
    private final BizDataLatestMapper dataLatestMapper;
    private final BizSysConfigMapper sysConfigMapper;
    private final BizDataService bizDataService;
    private final MigrationProbeService probeService;

    private volatile MigrationVerifyResp lastVerify;

    /**
     * 导入历史数据（FR-IO-06 第 3 步、FR-MIG-04 历史迁移）
     *
     * @param spec   连接参数
     * @param start  起始时间（含）
     * @param end    结束时间（含）
     * @param dryRun 是否仅预演
     * @return 导入结果
     */
    public MigrationImportResp importHistory(MigrationSpec spec, LocalDateTime start,
        LocalDateTime end, boolean dryRun) {
        final long startedAt = System.currentTimeMillis();
        MigrationImportResp resp = new MigrationImportResp();
        resp.setType("HISTORY");
        resp.setDryRun(dryRun);
        List<ImportRowErrorResp> errors = new ArrayList<>();
        this.checkRange(start, end);
        MigrationSourceClient source = probeService.client(spec);
        CheckUtils.throwIf(!source.hasTable(SOURCE_HISTORY), "源库缺少历史表：{}", SOURCE_HISTORY);
        Map<String, BizDeviceDO> devices = new HashMap<>();
        Map<String, BizChannelDO> channels = new HashMap<>();
        // 审计修复：整页收集后批量幂等写入（原先每条样本一次 count + 一次 insert + 一次实时值
        // select/update，1300 行历史要跑上万次 SQL，接口直接超时）
        Map<String, BizDataDO> latestCandidates = new HashMap<>();
        int imported = 0;
        int skipped = 0;
        int scanned = 0;
        // 分页游标用 (RecordTime, ID) 复合键：DR-03 的 tbhistory.ID 是字符串合成主键
        // （如 H<设备>-<节点>-<时间戳>），按数值 ID 翻页会导致游标不推进、死循环。
        LocalDateTime cursorTime = null;
        String cursorId = null;
        long maxRounds =
            MigrationSourceClient.MAX_ROWS / Math.max(1, properties.getBatchSize()) + 10L;
        long rounds = 0L;
        while (true) {
            CheckUtils.throwIf(++rounds > maxRounds, "源表分页轮次超过上限，疑似游标未推进，请检查源表主键");
            boolean firstBatch = cursorTime == null;
            String sql = "SELECT * FROM " + MigrationSourceClient.escape(SOURCE_HISTORY)
                + " WHERE RecordTime >= ? AND RecordTime <= ?"
                + (firstBatch ? "" : " AND (RecordTime > ? OR (RecordTime = ? AND ID > ?))")
                + " ORDER BY RecordTime, ID LIMIT ?";
            List<Map<String, Object>> page = firstBatch
                ? source.jdbc().queryForList(sql, start, end,
                    properties.getBatchSize())
                : source.jdbc().queryForList(sql, start, end, cursorTime,
                    cursorTime, cursorId, properties.getBatchSize());
            if (page.isEmpty()) {
                break;
            }
            List<BizDataDO> pending = new ArrayList<>(page.size() * 2);
            for (Map<String, Object> row : page) {
                scanned++;
                try {
                    String deviceName = MigrationValues.text(row.get("DeviceName"));
                    CheckUtils.throwIf(deviceName == null || deviceName.isBlank(), "设备名为空");
                    BizDeviceDO device = devices.get(deviceName);
                    if (device == null) {
                        device = deviceMapper.lambdaQuery().eq(BizDeviceDO::getName, deviceName)
                            .last("LIMIT 1").one();
                        CheckUtils.throwIf(device == null, "本系统缺少设备：{}，请先完成设备映射", deviceName);
                        devices.put(deviceName, device);
                    }
                    int nodeId = (int) MigrationValues.toLong(row.get("NodeId"), 1L);
                    LocalDateTime recordTime = MigrationValues.toTime(row.get("RecordTime"));
                    // DR-03：Hum 为模拟量 2、Tem 为模拟量 1
                    this.collectSample(device, nodeId, 2, row.get("Hum"), recordTime, channels,
                        pending,
                        latestCandidates);
                    this.collectSample(device, nodeId, 1, row.get("Tem"), recordTime, channels,
                        pending,
                        latestCandidates);
                } catch (Exception e) {
                    errors.add(new ImportRowErrorResp(scanned, e.getMessage()));
                }
            }
            Map<String, Object> lastRow = page.get(page.size() - 1);
            LocalDateTime nextTime = MigrationValues.toTime(lastRow.get("RecordTime"));
            String nextId = MigrationValues.text(lastRow.get("ID"));
            CheckUtils.throwIf(
                nextTime.equals(cursorTime) && java.util.Objects.equals(nextId, cursorId),
                "源表分页游标未推进（RecordTime={}，ID={}），请检查源表数据", nextTime, nextId);
            cursorTime = nextTime;
            cursorId = nextId;
            if (dryRun) {
                List<Long> sourceIds = pending.stream().map(BizDataDO::getSourceId).toList();
                Set<Long> existing = sourceIds.isEmpty() ? Set.of() : new HashSet<>(dataMapper
                    .selectExistingSourceIds(sourceIds));
                imported += pending.size() - existing.size();
                skipped += existing.size();
            } else if (!pending.isEmpty()) {
                int inserted = bizDataService.saveIdempotent(pending);
                imported += inserted;
                skipped += pending.size() - inserted;
            }
        }
        if (!dryRun) {
            for (BizDataDO candidate : latestCandidates.values()) {
                this.upsertLatest(candidate);
            }
        }
        resp.setScanned(scanned);
        resp.setImported(imported);
        resp.setSkipped(skipped);
        resp.setFailed(errors.size());
        resp.setErrors(errors.size() > 200 ? errors.subList(0, 200) : errors);
        resp.setDurationMs(System.currentTimeMillis() - startedAt);
        log.info("历史迁移完成：扫描 {} 行，写入 {}，跳过 {}，失败 {}", scanned, resp.getImported(), skipped,
            errors.size());
        return resp;
    }

    /**
     * 一致性校验（FR-MIG-06）
     *
     * @param spec  连接参数
     * @param start 起始时间
     * @param end   结束时间
     * @return 校验报告
     */
    public MigrationVerifyResp verify(MigrationSpec spec, LocalDateTime start, LocalDateTime end) {
        this.checkRange(start, end);
        MigrationVerifyResp resp = new MigrationVerifyResp();
        resp.setCheckedAt(LocalDateTime.now());
        resp.setStartTime(start);
        resp.setEndTime(end);
        MigrationSourceClient source = probeService.client(spec);
        CheckUtils.throwIf(!source.hasTable(SOURCE_HISTORY), "源库缺少历史表：{}", SOURCE_HISTORY);
        long sourceRows = source.jdbc()
            .queryForObject("SELECT COUNT(*) FROM " + MigrationSourceClient
                .escape(SOURCE_HISTORY) + " WHERE RecordTime >= ? AND RecordTime <= ?", Long.class,
                start,
                end);
        resp.setSourceRows(sourceRows);
        List<Map<String, Object>> sourceSamples = source.jdbc()
            .queryForList("SELECT DeviceName, NodeId,"
                + " Hum, Tem, RecordTime FROM " + MigrationSourceClient.escape(SOURCE_HISTORY)
                + " WHERE RecordTime >= ? AND RecordTime <= ? ORDER BY RecordTime LIMIT ?", start,
                end,
                Math.max(1, properties.getSampleSize()));
        int matched = 0;
        int mismatched = 0;
        List<String> deviceNames = new ArrayList<>();
        for (Map<String, Object> row : sourceSamples) {
            String deviceName = MigrationValues.text(row.get("DeviceName"));
            if (deviceName != null && !deviceNames.contains(deviceName)) {
                deviceNames.add(deviceName);
            }
            int nodeId = (int) MigrationValues.toLong(row.get("NodeId"), 1L);
            LocalDateTime recordTime = MigrationValues.toTime(row.get("RecordTime"));
            mismatched += this.compare(resp, deviceName, nodeId, 2, row.get("Hum"), recordTime);
            mismatched += this.compare(resp, deviceName, nodeId, 1, row.get("Tem"), recordTime);
        }
        matched = resp.getSamples().size() - mismatched;
        resp.setSampled(resp.getSamples().size());
        resp.setMatched(Math.max(matched, 0));
        resp.setMismatched(mismatched);
        // 审计修复：本系统行数只统计「源库时间窗内出现过的设备」。
        // 原实现统计窗口内全部 biz_data，会把其它设备的历史也算进去，条数对比没有意义
        // （实测源库 1367 行 vs 本系统 4098 行，其中大部分是别的设备的采集数据）。
        List<String> windowDevices = source.jdbc().queryForList("SELECT DISTINCT DeviceName FROM "
            + MigrationSourceClient.escape(SOURCE_HISTORY)
            + " WHERE RecordTime >= ? AND RecordTime <= ?", String.class, start, end);
        List<Long> scopeDeviceIds = new ArrayList<>();
        for (String deviceName : windowDevices) {
            if (deviceName == null || deviceName.isBlank()) {
                continue;
            }
            BizDeviceDO device = deviceMapper.lambdaQuery().eq(BizDeviceDO::getName, deviceName)
                .last("LIMIT 1").one();
            if (device != null) {
                scopeDeviceIds.add(device.getId());
            }
        }
        Long localRows = scopeDeviceIds.isEmpty() ? 0L : dataMapper.lambdaQuery()
            .in(BizDataDO::getDeviceId, scopeDeviceIds)
            .ge(BizDataDO::getRecordTime, start)
            .le(BizDataDO::getRecordTime, end)
            .count();
        resp.setLocalRows(localRows == null ? 0L : localRows);
        resp.setConsistent(mismatched == 0 && (resp.getSampled() == null || resp.getSampled() > 0));
        resp.setConclusion("源库 " + sourceRows + " 行，本系统（仅统计源库出现过的 "
            + scopeDeviceIds.size() + " 台设备）" + resp.getLocalRows() + " 行；抽样 "
            + (resp.getSampled() == null ? 0 : resp.getSampled()) + " 条，一致 " + resp.getMatched()
            + " 条，不一致 " + mismatched + " 条"
            + (Boolean.TRUE.equals(resp.getConsistent()) ? "，抽样一致" : "，存在差异请核对"));
        this.lastVerify = resp;
        log.info("一致性校验完成：{}", resp.getConclusion());
        return resp;
    }

    /**
     * 最近一次一致性校验报告
     *
     * @return 报告（可能为空）
     */
    public MigrationVerifyResp lastVerify() {
        return lastVerify;
    }

    /**
     * 当前迁移模式（FR-MIG-01）
     *
     * @return 模式
     */
    public String mode() {
        String configured = this.configValue(KEY_MODE);
        return configured == null || configured.isBlank() ? properties.getMode() : configured;
    }

    /**
     * 切换迁移模式（FR-MIG-01）
     *
     * @param mode 模式
     * @return 生效模式
     */
    public String setMode(String mode) {
        MigrationMode target = MigrationMode.of(mode);
        BizSysConfigDO existing =
            sysConfigMapper.lambdaQuery().eq(BizSysConfigDO::getCategory, "SYSTEM")
                .eq(BizSysConfigDO::getCode, KEY_MODE).last("LIMIT 1").one();
        if (existing == null) {
            BizSysConfigDO entity = new BizSysConfigDO();
            entity.setCategory("SYSTEM");
            entity.setName("迁移模式");
            entity.setCode(KEY_MODE);
            entity.setValue(target.name());
            entity.setDefaultValue(MigrationMode.OFF.name());
            entity.setDescription("OFF 仅本系统 / COEXIST 与仁科并存错峰 / SWITCHED 已切换（FR-MIG-01）");
            sysConfigMapper.insert(entity);
        } else {
            BizSysConfigDO update = new BizSysConfigDO();
            update.setId(existing.getId());
            update.setValue(target.name());
            sysConfigMapper.updateById(update);
        }
        log.warn("迁移模式已切换为 {}", target);
        return target.name();
    }

    /**
     * 迁移状态概览
     *
     * @return 状态
     */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("mode", this.mode());
        status.put("quietWindowEnabled", this.quietWindowEnabled());
        status.put("quietWindowStart", properties.getQuietWindowStart());
        status.put("quietWindowEnd", properties.getQuietWindowEnd());
        status.put("batchSize", properties.getBatchSize());
        status.put("sampleSize", properties.getSampleSize());
        status.put("lastVerify", lastVerify);
        // FR-MIG-05：回退口径为「自包含回退 + 设备交还」——只回退本平台自身的模式与数据，
        // 不启动、不调用、不依赖任何第三方原软件（防侵权）
        status.put("rollbackMode", "SELF_CONTAINED");
        status.put("rollbackScript", "tools/rollback-self-contained.ps1");
        status.put("supportedModes", List.of(MigrationMode.values()));
        return status;
    }

    /**
     * 当前是否处于错峰窗口（FR-MIG-01）
     *
     * @return 是否在窗口内
     */
    public boolean inQuietWindow() {
        return this.inWindow(properties.getQuietWindowStart(), properties.getQuietWindowEnd(),
            LocalDateTime.now());
    }

    /**
     * 是否启用错峰窗口
     *
     * @return 是否启用
     */
    public boolean quietWindowEnabled() {
        return MigrationMode.COEXIST.name().equals(this.mode()) && !properties.getQuietWindowStart()
            .equals(properties.getQuietWindowEnd());
    }

    /**
     * 收集一条样本：构造实体放入待写入列表，并记录该通道时间最新的一行用于最后刷新实时值
     *
     * @param device           设备
     * @param nodeId           节点号
     * @param analogNo         模拟量序号
     * @param raw              源库原始值
     * @param recordTime       记录时间
     * @param channels         通道缓存
     * @param pending          待写入列表
     * @param latestCandidates 每通道最新一行
     */
    private void collectSample(BizDeviceDO device, int nodeId, int analogNo, Object raw,
        LocalDateTime recordTime, Map<String, BizChannelDO> channels, List<BizDataDO> pending,
        Map<String, BizDataDO> latestCandidates) {
        if (raw == null) {
            return;
        }
        double value = Double.parseDouble(String.valueOf(raw));
        BizChannelDO channel = this.findChannel(device.getId(), nodeId, analogNo, channels);
        BizDataDO entity = new BizDataDO();
        entity.setDeviceId(device.getId());
        entity.setDeviceName(device.getName());
        entity.setDeviceAddr(device.getSlaveAddr());
        entity.setNodeId(nodeId);
        entity.setAnalogNo(analogNo);
        entity.setElementType(channel == null ? null : channel.getElementType());
        entity.setElementName(channel == null ? null : channel.getElementName());
        entity.setUnit(channel == null ? null : channel.getUnit());
        entity.setDataValue(value);
        entity.setRawValue(value);
        entity.setAlarmFlag(0);
        entity.setRecordTime(recordTime);
        entity.setSourceId(ImportService.sourceId(device.getId(), nodeId, analogNo, recordTime));
        pending.add(entity);
        String key = device.getId() + ":" + nodeId + ":" + analogNo;
        BizDataDO newest = latestCandidates.get(key);
        if (newest == null || newest.getRecordTime().isBefore(recordTime)) {
            latestCandidates.put(key, entity);
        }
    }

    /**
     * 通道查询（带缓存，重复行只取一条）
     *
     * @param deviceId 设备 ID
     * @param nodeId   节点号
     * @param analogNo 模拟量序号
     * @param channels 缓存
     * @return 通道
     */
    private BizChannelDO findChannel(Long deviceId, int nodeId, int analogNo,
        Map<String, BizChannelDO> channels) {
        return channels.computeIfAbsent(deviceId + ":" + nodeId + ":" + analogNo,
            key -> channelMapper
                .lambdaQuery().eq(BizChannelDO::getDeviceId, deviceId)
                .eq(BizChannelDO::getChannelNo,
                    nodeId)
                .eq(BizChannelDO::getAnalogNo, analogNo).last("LIMIT 1").one());
    }

    private void upsertLatest(BizDataDO data) {
        BizDataLatestDO latest = dataLatestMapper.lambdaQuery()
            .eq(BizDataLatestDO::getDeviceId, data
                .getDeviceId())
            .eq(BizDataLatestDO::getNodeId, data.getNodeId()).eq(BizDataLatestDO::getAnalogNo,
                data.getAnalogNo())
            .last("LIMIT 1")
            .one();
        if (latest != null && latest.getCollectTime() != null
            && latest.getCollectTime().isAfter(data
                .getRecordTime())) {
            return;
        }
        BizDataLatestDO entity = new BizDataLatestDO();
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

    private int compare(MigrationVerifyResp resp, String deviceName, int nodeId, int analogNo,
        Object raw, LocalDateTime recordTime) {
        if (raw == null) {
            return 0;
        }
        double sourceValue = Double.parseDouble(String.valueOf(raw));
        MigrationSampleResp sample = new MigrationSampleResp();
        sample.setDeviceName(deviceName);
        sample.setNodeId(nodeId);
        sample.setAnalogNo(analogNo);
        sample.setRecordTime(recordTime);
        sample.setSourceValue(sourceValue);
        BizDataDO local = dataMapper.lambdaQuery().eq(BizDataDO::getDeviceName, deviceName)
            .eq(BizDataDO::getNodeId, nodeId).eq(BizDataDO::getAnalogNo, analogNo)
            .eq(BizDataDO::getRecordTime, recordTime)
            .last("LIMIT 1")
            .one();
        sample.setLocalValue(local == null ? null : local.getDataValue());
        boolean consistent = local != null && local.getDataValue() != null && Math.abs(local
            .getDataValue() - sourceValue) <= TOLERANCE;
        sample.setConsistent(consistent);
        resp.getSamples().add(sample);
        return consistent ? 0 : 1;
    }

    private void checkRange(LocalDateTime start, LocalDateTime end) {
        CheckUtils.throwIf(start == null || end == null, "时间范围不能为空");
        CheckUtils.throwIf(start.isAfter(end), "起始时间不能晚于结束时间");
        CheckUtils.throwIf(start.plusDays(properties.getMaxRangeDays()).isBefore(end),
            "时间范围超过 {} 天",
            properties.getMaxRangeDays());
    }

    private String configValue(String key) {
        BizSysConfigDO row =
            sysConfigMapper.lambdaQuery().eq(BizSysConfigDO::getCategory, "SYSTEM").eq(
                BizSysConfigDO::getCode, key).last("LIMIT 1").one();
        return row == null ? null : row.getValue();
    }

    private boolean inWindow(String start, String end, LocalDateTime now) {
        if (start == null || end == null || start.equals(end)) {
            return false;
        }
        int from = this.minutes(start);
        int to = this.minutes(end);
        int current = now.getHour() * 60 + now.getMinute();
        if (from <= to) {
            return current >= from && current < to;
        }
        // 跨天窗口
        return current >= from || current < to;
    }

    private int minutes(String text) {
        String[] parts = text.split(":");
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return Math.max(0, Math.min(23, hour)) * 60 + Math.max(0, Math.min(59, minute));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("错峰窗口时间格式应为 HH:mm：" + text);
        }
    }

}
