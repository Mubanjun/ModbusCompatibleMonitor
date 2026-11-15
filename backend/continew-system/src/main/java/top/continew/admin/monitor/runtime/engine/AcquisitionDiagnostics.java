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

package top.continew.admin.monitor.runtime.engine;

import com.mbj.jdcompatiblemonitor.device.AnalogSample;
import com.mbj.jdcompatiblemonitor.device.ChannelValueReader;
import com.mbj.jdcompatiblemonitor.device.runtime.AcquisitionSettings;
import com.mbj.jdcompatiblemonitor.device.runtime.DeviceStatus;
import com.mbj.jdcompatiblemonitor.protocol.FrameSummary;
import com.mbj.jdcompatiblemonitor.protocol.FrameTrace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.runtime.config.AcquisitionProperties;
import top.continew.admin.monitor.runtime.model.AcquisitionSettingsResp;
import top.continew.admin.monitor.runtime.model.AcquisitionStatusResp;
import top.continew.admin.monitor.runtime.model.AnalogTestResp;
import top.continew.admin.monitor.runtime.model.ChannelTestResp;
import top.continew.admin.monitor.runtime.model.DeviceStatusResp;
import top.continew.admin.monitor.runtime.model.DeviceTestResp;
import top.continew.admin.monitor.runtime.model.PortStatusResp;
import top.continew.admin.monitor.runtime.model.SignalFrameResp;
import top.continew.admin.monitor.runtime.service.RuntimeSettingsService;
import top.continew.admin.monitor.runtime.session.ChannelBinding;
import top.continew.admin.monitor.runtime.session.DeviceSession;
import top.continew.admin.monitor.runtime.session.PortSession;
import top.continew.admin.monitor.runtime.signal.SignalEntry;
import top.continew.admin.monitor.runtime.signal.SignalRecorder;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 采集诊断视图（状态快照、单设备通讯测试、协议留痕展示）
 *
 * <p>从 {@link AcquisitionEngine} 抽出的只读/诊断部分：不参与轮询调度，只把会话与留痕
 * 转成接口模型，因此可以独立演进与单测（NFR-MAINT-03）。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Component
@RequiredArgsConstructor
public class AcquisitionDiagnostics {

    private final AcquisitionProperties properties;
    private final SignalRecorder signalRecorder;
    private final RuntimeSettingsService runtimeSettingsService;

    /**
     * 组装运行状态快照
     *
     * @param state 采集内核当前状态
     * @return 状态快照
     */
    public AcquisitionStatusResp snapshot(SnapshotState state) {
        AcquisitionSettings settings = state.settings();
        AcquisitionStatusResp resp = new AcquisitionStatusResp();
        resp.setRunning(state.running());
        resp.setPaused(state.paused());
        resp.setStartedAt(state.startedAt());
        resp.setUptimeMs(state.running() && state.startedAt() != null ? System.currentTimeMillis()
            - state.startedAt() : null);
        resp.setLastReloadTime(state.lastReloadTime());
        resp.setRoundCount(state.roundCount());
        resp.setDevicePollCount(state.devicePollCount());
        resp.setSampleCount(state.sampleCount());
        resp.setTraceEnabled(properties.isTraceEnabled());
        resp.setTraceDroppedCount(signalRecorder.droppedCount());
        resp.setSettings(AcquisitionSettingsResp.of(settings));
        resp.setLastError(state.lastError());
        int hour = LocalTime.now().getHour();
        List<PortStatusResp> portStatuses = new ArrayList<>();
        for (PortSession port : state.ports()) {
            portStatuses.add(this.toPortStatus(port, settings, hour));
        }
        portStatuses.sort(Comparator.comparing(PortStatusResp::getId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        resp.setPorts(portStatuses);
        return resp;
    }

    /**
     * 单设备通讯测试（FR-DEV-13）
     *
     * @param deviceId 设备 ID
     * @param device   设备会话
     * @param port     端口会话
     * @return 测试结果
     */
    public DeviceTestResp testDevice(Long deviceId, DeviceSession device, PortSession port) {
        DeviceTestResp resp = new DeviceTestResp();
        resp.setDeviceId(deviceId);
        resp.setDeviceName(device.getDevice().getName());
        resp.setSlaveAddr(device.slaveAddr());
        resp.setProtocol(device.getDevice().getProtocol());
        resp.setPortName(port.port().getPortName());
        AcquisitionSettings settings = runtimeSettingsService.current();
        int timeout = settings.resolveTimeout(device.getDevice().getTimeoutMs());
        long start = System.currentTimeMillis();
        boolean success = true;
        String error = null;
        for (ChannelBinding binding : device.getChannels()) {
            ChannelTestResp channelResp = new ChannelTestResp();
            channelResp.setChannelNo(binding.channelNo());
            channelResp.setRegAddr(binding.regAddr());
            try {
                int[] registers = port.readHoldingRegisters(device.slaveAddr(), binding.regAddr(),
                    binding.registerCount(), timeout);
                for (int register : registers) {
                    channelResp.getRegisters().add(register);
                }
                for (AnalogSample sample : ChannelValueReader.readSamples(binding.config(),
                    registers)) {
                    channelResp.getSamples().add(AnalogTestResp.of(sample,
                        AcquisitionElements.elementName(binding, sample.analogNo())));
                }
            } catch (Exception e) {
                success = false;
                error = e.getMessage();
                channelResp.setErrorMsg(e.getMessage());
            }
            resp.getChannels().add(channelResp);
        }
        resp.setDurationMs(System.currentTimeMillis() - start);
        resp.setSuccess(success);
        resp.setErrorMsg(error);
        for (FrameTrace trace : port.drainTraces()) {
            resp.getFrames().add(this.toFrame(port, device, trace,
                (int) (long) (resp.getDurationMs() == null ? 0L : resp.getDurationMs())));
        }
        return resp;
    }

    /**
     * 把一条协议留痕转换为展示模型
     *
     * @param port       端口会话
     * @param device     设备会话
     * @param trace      协议留痕
     * @param durationMs 耗时
     * @return 信令展示模型
     */
    public SignalFrameResp toFrame(PortSession port, DeviceSession device, FrameTrace trace,
        int durationMs) {
        boolean pureError = trace.note() != null && trace.frame().length == 0;
        String direction = pureError ? "ERROR" : (trace.outbound() ? "OUT" : "IN");
        String parsed = pureError ? trace.note()
            : FrameSummary.describe(AcquisitionElements.isTcp(port), trace.outbound(),
                trace.frame());
        SignalEntry entry = new SignalEntry(signalRecorder.currentSequence(), trace.timeMillis(),
            port.port().getId(), port.port().getPortName(), device.deviceId(),
            device.getDevice().getName(),
            trace.slaveAddress() < 0 ? null : trace.slaveAddress(), direction,
            FrameSummary.toHex(trace.frame()), trace.frame().length, parsed, durationMs,
            !pureError, pureError ? trace.note() : null);
        return SignalFrameResp.of(entry);
    }

    /**
     * 组装端口状态
     *
     * @param port     端口会话
     * @param settings 采集参数
     * @param hour     当前小时
     * @return 端口状态
     */
    private PortStatusResp toPortStatus(PortSession port, AcquisitionSettings settings, int hour) {
        PortStatusResp resp = new PortStatusResp();
        resp.setId(port.port().getId());
        resp.setName(port.port().getName());
        resp.setPortType(port.port().getPortType());
        resp.setPortName(port.port().getPortName());
        resp.setConnected(port.isConnected());
        resp.setPaused(port.getPaused().get());
        resp.setBusy(port.getBusy().get());
        resp.setLastError(port.getLastError());
        List<DeviceStatusResp> list = new ArrayList<>();
        for (DeviceSession device : port.devices()) {
            list.add(this.toDeviceStatus(port, device, settings, hour));
        }
        resp.setDevices(list);
        resp.setDeviceCount(list.size());
        return resp;
    }

    /**
     * 组装单台设备的状态
     *
     * @param port     端口会话
     * @param device   设备会话
     * @param settings 采集参数
     * @param hour     当前小时
     * @return 设备状态
     */
    private DeviceStatusResp toDeviceStatus(PortSession port, DeviceSession device,
        AcquisitionSettings settings, int hour) {
        DeviceStatusResp resp = new DeviceStatusResp();
        resp.setId(device.deviceId());
        resp.setName(device.getDevice().getName());
        resp.setSlaveAddr(device.getDevice().getSlaveAddr());
        resp.setProtocol(device.getDevice().getProtocol());
        DeviceStatus status = device.getStatus();
        resp.setStatus(status.name());
        resp.setStatusLabel(status.label());
        resp.setEnabled(device.isActive());
        resp.setPaused(device.getPaused().get());
        int interval = settings.applyTimeOfDay(settings
            .resolveInterval(port.port().getIntervalMs(), device.getDevice().getIntervalMs()),
            hour);
        resp.setIntervalMs(interval);
        resp.setChannelCount(device.getChannels().size());
        resp.setLastPollTime(device.getLastPollTime().get() == 0 ? null
            : device.getLastPollTime().get());
        resp.setLastSuccessTime(device.getMetrics().lastSuccessTime() == 0 ? null
            : device.getMetrics().lastSuccessTime());
        resp.setPollCount(device.getMetrics().pollCount());
        resp.setSuccessCount(device.getMetrics().successCount());
        resp.setTimeoutCount(device.getMetrics().timeoutCount());
        resp.setErrorCount(device.getMetrics().errorCount());
        resp.setSuccessRate(device.getMetrics().successRate());
        resp.setAverageDurationMs(device.getMetrics().averageDurationMs());
        resp.setCircuitState(device.getBreaker().state().name());
        resp.setConsecutiveFailures(device.getBreaker().consecutiveFailures());
        resp.setTripCount(device.getBreaker().tripCount());
        resp.setBudgetExceeded(device.getBudgetExceeded().get());
        resp.setLastError(device.getMetrics().lastError());
        return resp;
    }

    /**
     * 采集内核状态上下文（快照组装所需的最小状态）
     *
     * @param running         是否运行中
     * @param paused          是否暂停
     * @param startedAt       启动时间
     * @param lastReloadTime  最近重载时间
     * @param roundCount      调度轮次
     * @param devicePollCount 设备轮询次数
     * @param sampleCount     采样条数
     * @param lastError       最近错误
     * @param settings        生效采集参数
     * @param ports           端口会话集合
     */
    public record SnapshotState(boolean running, boolean paused, Long startedAt,
        Long lastReloadTime,
        long roundCount, long devicePollCount, long sampleCount, String lastError,
        AcquisitionSettings settings, Collection<PortSession> ports) {
    }
}
