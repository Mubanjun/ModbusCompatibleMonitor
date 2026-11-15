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

package top.continew.admin.monitor.runtime.session;

import com.mbj.jdcompatiblemonitor.protocol.DeviceProtocol;
import com.mbj.jdcompatiblemonitor.protocol.Protocols;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizPortDO;
import top.continew.admin.monitor.runtime.engine.AcquisitionEngine;
import top.continew.admin.monitor.runtime.service.RuntimeSettingsService;
import top.continew.starter.core.util.validation.CheckUtils;

import java.util.function.Function;

/**
 * 设备读写会话支撑（继电器、校时等写类操作共用）。
 *
 * <p>优先级：复用采集内核已建立的端口会话（同一条 485 总线只保留一次物理连接），
 * 设备未加载时临时建链、用完即关。端口会话内部用公平锁串行收发，因此各调用方
 * 无需自行加锁（见 {@link PortSession}）。</p>
 *
 * <p>本次抽取自 {@code RelayService} 的同类实现，供设备校时复用，避免同一套
 * 建链逻辑出现多份拷贝。</p>
 *
 * @author jdrk
 * @since 2026/10/1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceSessionSupport {

    private final AcquisitionEngine acquisitionEngine;
    private final BizPortMapper bizPortMapper;
    private final RuntimeSettingsService runtimeSettingsService;

    /**
     * 复用采集端口会话执行读写；设备未加载时建立临时连接并在用完后关闭
     *
     * @param device 设备
     * @param action 动作
     * @param <T>    返回值类型
     * @return 动作结果
     */
    public <T> T usingSession(BizDeviceDO device, Function<PortSession, T> action) {
        PortSession session = acquisitionEngine.sessionOf(device.getId());
        if (session != null) {
            return action.apply(session);
        }
        try (PortSession temporary = this.temporarySession(device)) {
            return action.apply(temporary);
        }
    }

    /**
     * 建立临时端口会话（调用方负责关闭）
     *
     * @param device 设备
     * @return 端口会话
     */
    public PortSession temporarySession(BizDeviceDO device) {
        BizPortDO port = bizPortMapper.selectById(device.getPortId());
        CheckUtils.throwIf(port == null, "设备所属端口不存在");
        int timeout = this.timeout(device);
        DeviceProtocol protocol = Protocols.create(device.getProtocol(), port.getPortType(), port
            .getPortName(), port.getBaudRate() == null ? 4800 : port.getBaudRate(),
            port.getDataBits() == null ? 8 : port.getDataBits(), port.getStopBits() == null ? 1
                : port.getStopBits(),
            port.getParity() == null ? 0 : port.getParity(), port
                .getFlowControl() == null ? 0 : port.getFlowControl(),
            timeout, 1);
        return new PortSession(port, protocol);
    }

    /**
     * 设备级超时（毫秒），取运行时采集参数
     *
     * @param device 设备
     * @return 超时毫秒数
     */
    public int timeout(BizDeviceDO device) {
        return runtimeSettingsService.current().resolveTimeout(device.getTimeoutMs());
    }
}
