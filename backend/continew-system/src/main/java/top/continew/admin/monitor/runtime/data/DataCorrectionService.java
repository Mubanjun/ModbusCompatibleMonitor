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

package top.continew.admin.monitor.runtime.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizChannelMapper;
import top.continew.admin.monitor.mapper.BizDataLatestMapper;
import top.continew.admin.monitor.mapper.BizDataMapper;
import top.continew.admin.monitor.model.entity.BizChannelDO;
import top.continew.admin.monitor.model.entity.BizDataDO;
import top.continew.admin.monitor.model.entity.BizDataLatestDO;
import top.continew.admin.monitor.model.req.DataCorrectReq;
import top.continew.admin.monitor.model.req.DataRecalculateReq;
import top.continew.admin.monitor.runtime.model.DataCorrectResp;
import top.continew.admin.monitor.runtime.model.DataRecalculateResp;
import top.continew.starter.core.util.validation.CheckUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史数据补算与修正（FR-WEB-08）。
 *
 * <p>与查询时的 `recalculate` 展示重算不同，这里会**落库**：</p>
 * <ul>
 *   <li>单点修正：按 ID 或「设备+节点+模拟量+记录时间」定位，改工程值或原始值
 *       （只给原始值时按通道系数与偏移补算工程值），必要时刷新实时值缓存；</li>
 *   <li>批量补算：按时间范围与设备筛选，用当前通道系数对已有原始值重算工程值，
 *       只更新真正变化的行，支持 dryRun 预演。</li>
 * </ul>
 *
 * <p>人工修正依赖基座请求审计留痕（sys_log 记录请求体与操作人），
 * 本类另外在返回体里给出修正前后取值便于比对。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataCorrectionService {

    /** 批量补算单页条数 */
    private static final int PAGE_SIZE = 2000;

    /** 批量补算上限 */
    private static final long MAX_ROWS = 1_000_000L;

    /** 错误明细上限 */
    private static final int MAX_ERRORS = 20;

    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BizDataMapper dataMapper;
    private final BizDataLatestMapper dataLatestMapper;
    private final BizChannelMapper channelMapper;

    /**
     * 单点修正
     *
     * @param req 请求
     * @return 修正结果
     */
    public DataCorrectResp correct(DataCorrectReq req) {
        CheckUtils.throwIf(req == null, "请求不能为空");
        CheckUtils.throwIf(
            req.getId() == null && (req.getDeviceId() == null || req.getNodeId() == null
                || req.getAnalogNo() == null || req.getRecordTime() == null),
            "请提供记录 ID，或完整的设备/节点/模拟量/记录时间");
        BizDataDO row = req.getId() != null ? dataMapper.selectById(req.getId()) : dataMapper
            .lambdaQuery().eq(BizDataDO::getDeviceId, req.getDeviceId())
            .eq(BizDataDO::getNodeId, req
                .getNodeId())
            .eq(BizDataDO::getAnalogNo, req.getAnalogNo()).eq(BizDataDO::getRecordTime,
                req.getRecordTime())
            .one();
        CheckUtils.throwIf(row == null, "未找到待修正的历史数据");
        DataCorrectResp resp = new DataCorrectResp();
        resp.setId(row.getId());
        resp.setDeviceName(row.getDeviceName());
        resp.setNodeId(row.getNodeId());
        resp.setAnalogNo(row.getAnalogNo());
        resp.setRecordTime(row.getRecordTime() == null ? null : row.getRecordTime().format(TIME));
        resp.setBeforeValue(row.getDataValue());
        resp.setBeforeRawValue(row.getRawValue());
        BizChannelDO channel =
            this.findChannel(row.getDeviceId(), row.getNodeId(), row.getAnalogNo());
        Double dataValue = req.getDataValue();
        Double rawValue = req.getRawValue() == null ? row.getRawValue() : req.getRawValue();
        if (dataValue == null && req.getRawValue() != null) {
            CheckUtils.throwIf(channel == null, "该通道未配置系数，无法按原始值补算工程值");
            dataValue = this.engineering(req.getRawValue(), channel);
        }
        CheckUtils.throwIf(dataValue == null && req.getRawValue() == null, "请至少提供工程值或原始值");
        if (dataValue == null) {
            dataValue = row.getDataValue();
        }
        BizDataDO update = new BizDataDO();
        update.setId(row.getId());
        update.setDataValue(dataValue);
        update.setRawValue(rawValue);
        dataMapper.updateById(update);
        resp.setAfterValue(dataValue);
        resp.setAfterRawValue(rawValue);
        resp.setLatestRefreshed(this.refreshLatest(row, dataValue, rawValue));
        resp.setMessage("已修正" + (req.getReason() == null || req.getReason().isBlank() ? "" : "（原因："
            + req.getReason() + "）"));
        log.warn("历史数据修正：id={} 设备={} 节点={} 模拟量={} 时间={} {} -> {}（原因：{}）", row.getId(), row
            .getDeviceName(), row.getNodeId(), row.getAnalogNo(), resp.getRecordTime(),
            resp
                .getBeforeValue(),
            dataValue, req.getReason());
        return resp;
    }

    /**
     * 批量补算
     *
     * @param req 请求
     * @return 补算结果
     */
    public DataRecalculateResp recalculate(DataRecalculateReq req) {
        CheckUtils.throwIf(req == null || req.getStartTime() == null || req.getEndTime() == null,
            "时间范围不能为空");
        CheckUtils.throwIf(req.getStartTime().isAfter(req.getEndTime()), "起始时间不能晚于结束时间");
        long start = System.currentTimeMillis();
        boolean dryRun = Boolean.TRUE.equals(req.getDryRun());
        DataRecalculateResp resp = new DataRecalculateResp();
        resp.setDryRun(dryRun);
        Map<String, BizChannelDO> channels = new HashMap<>();
        // 审计修复：批量补算不再逐行刷新实时值（N+1 查询），改为按通道收集最新一行，扫完统一刷新
        Map<String, LatestCandidate> latestCandidates = new HashMap<>();
        Long lastId = null;
        while (resp.getScanned() < MAX_ROWS) {
            LambdaQueryWrapper<BizDataDO> wrapper = Wrappers.<BizDataDO>lambdaQuery()
                .gt(lastId != null,
                    BizDataDO::getId, lastId)
                .eq(req.getDeviceId() != null, BizDataDO::getDeviceId, req
                    .getDeviceId())
                .eq(req.getNodeId() != null, BizDataDO::getNodeId, req.getNodeId())
                .eq(req.getAnalogNo() != null, BizDataDO::getAnalogNo, req.getAnalogNo())
                .ge(BizDataDO::getRecordTime, req.getStartTime())
                .le(BizDataDO::getRecordTime, req.getEndTime())
                .orderByAsc(BizDataDO::getId).last("LIMIT " + PAGE_SIZE);
            List<BizDataDO> page = dataMapper.selectList(wrapper);
            if (page.isEmpty()) {
                break;
            }
            for (BizDataDO row : page) {
                resp.setScanned(resp.getScanned() + 1);
                BizChannelDO channel =
                    channels.computeIfAbsent(this.key(row), key -> this.findChannel(row
                        .getDeviceId(), row.getNodeId(), row.getAnalogNo()));
                if (channel == null || row.getRawValue() == null) {
                    resp.setSkipped(resp.getSkipped() + 1);
                    continue;
                }
                try {
                    double recalculated = this.engineering(row.getRawValue(), channel);
                    if (row.getDataValue() != null
                        && Math.abs(row.getDataValue() - recalculated) < 1e-9) {
                        resp.setUnchanged(resp.getUnchanged() + 1);
                        continue;
                    }
                    if (!dryRun) {
                        BizDataDO update = new BizDataDO();
                        update.setId(row.getId());
                        update.setDataValue(recalculated);
                        dataMapper.updateById(update);
                        this.collectLatest(latestCandidates, row, recalculated);
                    }
                    resp.setUpdated(resp.getUpdated() + 1);
                } catch (Exception e) {
                    resp.setFailed(resp.getFailed() + 1);
                    if (resp.getErrors().size() < MAX_ERRORS) {
                        resp.getErrors().add("id=" + row.getId() + "：" + e.getMessage());
                    }
                }
            }
            lastId = page.get(page.size() - 1).getId();
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        for (LatestCandidate candidate : latestCandidates.values()) {
            this.refreshLatest(candidate.row(), candidate.dataValue(),
                candidate.row().getRawValue());
        }
        resp.setDurationMs(System.currentTimeMillis() - start);
        log.warn("历史数据批量补算：{}扫描 {}，更新 {}，无需变更 {}，跳过 {}，失败 {}，刷新实时值通道 {}", dryRun
            ? "（预演）" : "", resp.getScanned(), resp.getUpdated(), resp.getUnchanged(),
            resp.getSkipped(),
            resp.getFailed(), dryRun ? 0 : latestCandidates.size());
        return resp;
    }

    /**
     * 记录通道内时间最新的一行，供批量补算结束后统一刷新实时值
     *
     * <p>审计修复：原先每更新一行就刷一次实时值（每行一次 select + update），
     * 大范围补算会退化成 N+1 查询；改为按通道保留最新一行，扫完统一刷新。</p>
     *
     * @param candidates 候选集合
     * @param row        已更新的行
     * @param dataValue  修正后的工程值
     */
    private void collectLatest(Map<String, LatestCandidate> candidates, BizDataDO row,
        double dataValue) {
        if (row.getRecordTime() == null) {
            return;
        }
        LatestCandidate exists = candidates.get(this.key(row));
        if (exists == null || exists.row().getRecordTime().isBefore(row.getRecordTime())) {
            candidates.put(this.key(row), new LatestCandidate(row, dataValue));
        }
    }

    private double engineering(double rawValue, BizChannelDO channel) {
        BigDecimal coefficient = channel.getCoefficient() == null ? BigDecimal.ONE : channel
            .getCoefficient();
        BigDecimal offset = channel.getOffsetValue() == null ? BigDecimal.ZERO : channel
            .getOffsetValue();
        return BigDecimal.valueOf(rawValue).multiply(coefficient).add(offset).doubleValue();
    }

    private boolean refreshLatest(BizDataDO row, Double dataValue, Double rawValue) {
        if (row.getDeviceId() == null || row.getNodeId() == null || row.getAnalogNo() == null || row
            .getRecordTime() == null) {
            return false;
        }
        BizDataLatestDO latest = dataLatestMapper.lambdaQuery().eq(BizDataLatestDO::getDeviceId, row
            .getDeviceId()).eq(BizDataLatestDO::getNodeId, row.getNodeId())
            .eq(BizDataLatestDO::getAnalogNo,
                row.getAnalogNo())
            .one();
        if (latest == null) {
            return false;
        }
        if (latest.getCollectTime() != null
            && latest.getCollectTime().isAfter(row.getRecordTime())) {
            return false;
        }
        BizDataLatestDO update = new BizDataLatestDO();
        update.setId(latest.getId());
        update.setDataValue(dataValue);
        update.setRawValue(rawValue);
        update.setCollectTime(row.getRecordTime());
        dataLatestMapper.updateById(update);
        return true;
    }

    private BizChannelDO findChannel(Long deviceId, Integer nodeId, Integer analogNo) {
        if (deviceId == null || nodeId == null || analogNo == null) {
            return null;
        }
        return channelMapper.lambdaQuery().eq(BizChannelDO::getDeviceId, deviceId)
            .eq(BizChannelDO::getChannelNo, nodeId).eq(BizChannelDO::getAnalogNo, analogNo).one();
    }

    private String key(BizDataDO row) {
        return row.getDeviceId() + ":" + row.getNodeId() + ":" + row.getAnalogNo();
    }

    /**
     * 批量补算中的实时值刷新候选
     *
     * @param row       已更新的行
     * @param dataValue 修正后的工程值
     */
    private record LatestCandidate(BizDataDO row, double dataValue) {
    }
}
