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

package top.continew.admin.monitor.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.req.BizDeviceLocationReq;
import top.continew.starter.core.util.validation.CheckUtils;

/** 设备位置维护。 */
@Service
@RequiredArgsConstructor
public class BizDeviceLocationService {

    private final BizDeviceMapper deviceMapper;

    /** 单台设备位置更新。 */
    @Transactional
    public void update(Long id, BizDeviceLocationReq req) {
        validate(req);
        CheckUtils.throwIf(deviceMapper.selectById(id) == null, "设备不存在");
        deviceMapper.update(null, wrapper(id, req));
    }

    /** 批量更新，返回成功数与逐行错误。 */
    @Transactional
    public BatchResult batch(List<BizDeviceLocationReq> reqs) {
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < reqs.size(); i++) {
            BizDeviceLocationReq req = reqs.get(i);
            try {
                CheckUtils.throwIf(req.getDeviceId() == null, "deviceId 为空");
                update(req.getDeviceId(), req);
                success++;
            } catch (Exception e) {
                errors.add("第 " + (i + 1) + " 行: " + e.getMessage());
            }
        }
        return new BatchResult(success, errors);
    }

    private void validate(BizDeviceLocationReq req) {
        CheckUtils.throwIf((req.getLongitude() == null) != (req.getLatitude() == null),
            "经度和纬度必须同时填写");
    }

    private LambdaUpdateWrapper<BizDeviceDO> wrapper(Long id, BizDeviceLocationReq req) {
        LambdaUpdateWrapper<BizDeviceDO> w =
            new LambdaUpdateWrapper<BizDeviceDO>().eq(BizDeviceDO::getId, id);
        if (req.getLongitude() != null) {
            w.set(BizDeviceDO::getLongitude, req.getLongitude())
                .set(BizDeviceDO::getLatitude, req.getLatitude());
        }
        if (req.getElevation() != null) {
            w.set(BizDeviceDO::getElevation, req.getElevation());
        }
        if (req.getLocation() != null) {
            w.set(BizDeviceDO::getLocation, req.getLocation());
        }
        if (req.getAddress() != null) {
            w.set(BizDeviceDO::getAddress, req.getAddress());
        }
        return w;
    }

    /** 批量结果。 */
    public record BatchResult(int successCount, List<String> errors) {
    }
}
