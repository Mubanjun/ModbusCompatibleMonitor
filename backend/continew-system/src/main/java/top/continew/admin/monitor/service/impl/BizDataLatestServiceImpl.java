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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.base.service.BaseServiceImpl;
import top.continew.admin.monitor.mapper.BizDataLatestMapper;
import top.continew.admin.monitor.model.entity.BizDataLatestDO;
import top.continew.admin.monitor.model.query.BizDataLatestQuery;
import top.continew.admin.monitor.model.req.BizDataLatestReq;
import top.continew.admin.monitor.model.resp.BizDataLatestResp;
import top.continew.admin.monitor.service.BizDataLatestService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通道实时值业务实现
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Service
@RequiredArgsConstructor
// @formatter:off
// 泛型参数拼接后单行会超出 Checkstyle 的 150 字符上限，而 Spotless 不会拆分泛型，故此处手工换行并豁免格式化
public class BizDataLatestServiceImpl
    extends BaseServiceImpl<BizDataLatestMapper, BizDataLatestDO, BizDataLatestResp,
        BizDataLatestResp, BizDataLatestQuery, BizDataLatestReq>
    implements BizDataLatestService {
    // @formatter:on

    @Override
    public int upsertAll(List<BizDataLatestDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 批内按唯一键 `(device_id, node_id, analog_no)` 先去重（只保留最后一条 = 最新样本）。
        // 缺陷 63：暂存落地是「一条样本一行」，同一通道在一个批次里出现多次是常态（轮询比落地周期快、
        // 或数据库恢复后一次取回积压的 500 行）。若该键在 biz_data_latest 里还不存在（新设备/新通道、
        // 换库恢复），这些行会全部进 inserts → saveBatch 撞唯一索引 uk_device_node_analog 抛
        // DuplicateKeyException → 整批退回 PENDING → 下一周期又取到同一批 → 上传通道永久卡死，
        // 暂存涨到上限后被按最旧清理（丢数据）。去重后每键只插一行，问题消失。
        Map<String, BizDataLatestDO> deduped = new LinkedHashMap<>();
        for (BizDataLatestDO row : rows) {
            deduped.put(this.key(row.getDeviceId(), row.getNodeId(), row.getAnalogNo()), row);
        }
        Set<Long> deviceIds = new HashSet<>();
        for (BizDataLatestDO row : deduped.values()) {
            if (row.getDeviceId() != null) {
                deviceIds.add(row.getDeviceId());
            }
        }
        Map<String, BizDataLatestDO> existing = new HashMap<>();
        if (!deviceIds.isEmpty()) {
            for (BizDataLatestDO row : baseMapper.lambdaQuery()
                .in(BizDataLatestDO::getDeviceId, deviceIds)
                .list()) {
                existing.put(this.key(row.getDeviceId(), row.getNodeId(), row.getAnalogNo()), row);
            }
        }
        List<BizDataLatestDO> inserts = new ArrayList<>();
        List<BizDataLatestDO> updates = new ArrayList<>();
        for (BizDataLatestDO row : deduped.values()) {
            BizDataLatestDO found = existing.get(this.key(row.getDeviceId(), row.getNodeId(), row
                .getAnalogNo()));
            if (found == null) {
                inserts.add(row);
            } else {
                row.setId(found.getId());
                updates.add(row);
            }
        }
        if (!inserts.isEmpty()) {
            saveBatch(inserts, inserts.size());
        }
        if (!updates.isEmpty()) {
            updateBatchById(updates, updates.size());
        }
        return rows.size();
    }

    @Override
    public int updateDeviceStatus(Long deviceId, String status) {
        if (deviceId == null || status == null) {
            return 0;
        }
        return baseMapper.update(null, Wrappers.<BizDataLatestDO>lambdaUpdate()
            .set(BizDataLatestDO::getDeviceStatus, status)
            .eq(BizDataLatestDO::getDeviceId, deviceId));
    }

    /**
     * 组装实时值唯一键
     *
     * @param deviceId 设备 ID
     * @param nodeId   节点编号
     * @param analogNo 模拟量序号
     * @return 唯一键
     */
    private String key(Long deviceId, Integer nodeId, Integer analogNo) {
        return deviceId + "-" + nodeId + "-" + analogNo;
    }
}
