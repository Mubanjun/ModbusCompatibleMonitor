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

package top.continew.admin.monitor.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizChannelMapper;
import top.continew.admin.monitor.mapper.BizDataMapper;
import top.continew.admin.monitor.model.entity.BizChannelDO;
import top.continew.admin.monitor.model.entity.BizDataDO;
import top.continew.admin.monitor.model.query.BizDataQuery;
import top.continew.admin.monitor.model.req.BizDataReq;
import top.continew.admin.monitor.model.resp.BizDataResp;
import top.continew.admin.monitor.model.resp.DataAggregateResp;
import top.continew.admin.monitor.service.BizDataService;
import top.continew.starter.core.util.validation.CheckUtils;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采集历史数据业务实现
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
// @formatter:off
// 泛型参数拼接后单行会超出 Checkstyle 的 150 字符上限，而 Spotless 不会拆分泛型，故此处手工换行并豁免格式化
public class BizDataServiceImpl
    extends BaseServiceImpl<BizDataMapper, BizDataDO, BizDataResp, BizDataResp, BizDataQuery,
        BizDataReq>
    implements BizDataService {
    // @formatter:on

    /** 分钟粒度的时间桶格式 */
    private static final String PATTERN_MINUTE = "%Y-%m-%d %H:%i";
    /** 小时粒度的时间桶格式 */
    private static final String PATTERN_HOUR = "%Y-%m-%d %H:00";
    /** 天粒度的时间桶格式 */
    private static final String PATTERN_DAY = "%Y-%m-%d";
    /** 曲线单次返回的默认点数上限 */
    private static final int CURVE_DEFAULT_LIMIT = 2000;
    /** 曲线单次返回的硬上限（防止误传大值把整段历史拉回前端） */
    private static final int CURVE_MAX_LIMIT = 20000;

    private final BizChannelMapper bizChannelMapper;

    @Override
    public PageResp<BizDataResp> page(BizDataQuery query, PageQuery pageQuery) {
        PageResp<BizDataResp> result = super.page(query, pageQuery);
        List<BizDataResp> rows = result == null ? null : result.getList();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        if (!Boolean.TRUE.equals(query.getRecalculate())) {
            rows.forEach(row -> row.setRecalculated(false));
            return result;
        }
        // FR-WEB-08：按当前通道系数与偏移重算工程值，仅影响展示、不落库覆盖
        Map<String, BizChannelDO> channelIndex = this.loadChannelIndex(rows);
        for (BizDataResp row : rows) {
            BizChannelDO channel = channelIndex.get(this.channelKey(row.getDeviceId(), row
                .getNodeId(), row.getAnalogNo()));
            if (channel == null || row.getRawValue() == null) {
                row.setRecalculated(false);
                continue;
            }
            BigDecimal coefficient = channel.getCoefficient() == null ? BigDecimal.ONE
                : channel.getCoefficient();
            BigDecimal offset = channel.getOffsetValue() == null ? BigDecimal.ZERO
                : channel.getOffsetValue();
            double recalculated = BigDecimal.valueOf(row.getRawValue())
                .multiply(coefficient)
                .add(offset)
                .doubleValue();
            row.setDataValue(recalculated);
            row.setRecalculated(true);
        }
        return result;
    }

    @Override
    public int saveIdempotent(List<BizDataDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 批内也要去重：源库同一秒内可能有多条同设备/节点/模拟量的记录，它们的 source_id 相同
        // （FNV-1a 由 设备+节点+模拟量+记录时间 生成）。不去重会直接撞唯一索引 uk_data_source_id，
        // 让整批写入失败、接口 500（实测 27k 行历史迁移撞到）。这里同一 source_id 保留最后一条。
        Map<Long, BizDataDO> unique = new LinkedHashMap<>();
        List<BizDataDO> withoutSourceId = new ArrayList<>();
        for (BizDataDO row : rows) {
            if (row.getSourceId() == null) {
                withoutSourceId.add(row);
            } else {
                unique.put(row.getSourceId(), row);
            }
        }
        Set<Long> existing = unique.isEmpty() ? Set.of() : new HashSet<>(baseMapper
            .selectExistingSourceIds(unique.keySet()));
        List<BizDataDO> pending = new ArrayList<>(unique.size() + withoutSourceId.size());
        for (BizDataDO row : unique.values()) {
            if (!existing.contains(row.getSourceId())) {
                pending.add(row);
            }
        }
        pending.addAll(withoutSourceId);
        if (pending.isEmpty()) {
            return 0;
        }
        saveBatch(pending, pending.size());
        return pending.size();
    }

    @Override
    public List<BizDataResp> listCurve(BizDataQuery query, Integer limit) {
        CheckUtils.throwIf(query.getStartTime() == null || query.getEndTime() == null,
            "曲线查询必须指定起始与结束时间，避免全表扫描");
        CheckUtils.throwIf(query.getStartTime().isAfter(query.getEndTime()),
            "曲线查询的起始时间不能晚于结束时间");
        int capped =
            limit == null || limit <= 0 ? CURVE_DEFAULT_LIMIT : Math.min(limit, CURVE_MAX_LIMIT);
        return baseMapper.listCurve(query.getDeviceId(), query.getDeviceAddr(), query.getNodeId(),
            query.getAnalogNo(), query.getStartTime(), query.getEndTime(), capped);
    }

    @Override
    public int purgeBefore(LocalDateTime deadline) {
        return baseMapper.purgeBefore(deadline);
    }

    @Override
    public List<DataAggregateResp> aggregate(BizDataQuery query) {
        CheckUtils.throwIf(query.getStartTime() == null || query.getEndTime() == null,
            "聚合查询必须指定起始与结束时间，避免全表扫描");
        String granularity = query.getGranularity() == null ? "HOUR" : query.getGranularity()
            .toUpperCase();
        String pattern = switch (granularity) {
            case "MINUTE" -> PATTERN_MINUTE;
            case "DAY" -> PATTERN_DAY;
            case "HOUR" -> PATTERN_HOUR;
            default -> throw new IllegalArgumentException("不支持的聚合粒度：" + query.getGranularity());
        };
        return baseMapper.aggregate(pattern, query.getDeviceId(), query.getNodeId(),
            query.getAnalogNo(), query.getStartTime(), query.getEndTime());
    }

    /**
     * 加载结果涉及的通道配置，供重算使用
     *
     * @param rows 历史数据
     * @return 通道索引（设备-通道-模拟量）
     */
    private Map<String, BizChannelDO> loadChannelIndex(List<BizDataResp> rows) {
        Set<Long> deviceIds = new HashSet<>();
        for (BizDataResp row : rows) {
            if (row.getDeviceId() != null) {
                deviceIds.add(row.getDeviceId());
            }
        }
        Map<String, BizChannelDO> index = new HashMap<>();
        if (deviceIds.isEmpty()) {
            return index;
        }
        List<BizChannelDO> channels = bizChannelMapper.lambdaQuery()
            .in(BizChannelDO::getDeviceId, deviceIds)
            .list();
        for (BizChannelDO channel : channels) {
            index.put(this.channelKey(channel.getDeviceId(), channel.getChannelNo(), channel
                .getAnalogNo()), channel);
        }
        return index;
    }

    /**
     * 组装通道索引键
     *
     * @param deviceId 设备 ID
     * @param nodeId   节点编号
     * @param analogNo 模拟量序号
     * @return 索引键
     */
    private String channelKey(Long deviceId, Integer nodeId, Integer analogNo) {
        return deviceId + "-" + nodeId + "-" + analogNo;
    }
}
