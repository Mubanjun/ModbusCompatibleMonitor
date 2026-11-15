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

import com.mbj.jdcompatiblemonitor.common.Hex;
import com.mbj.jdcompatiblemonitor.device.AnalogDefinition;
import com.mbj.jdcompatiblemonitor.device.AnalogSample;
import com.mbj.jdcompatiblemonitor.device.ChannelValueReader;
import com.mbj.jdcompatiblemonitor.device.runtime.AcquisitionSample;
import com.mbj.jdcompatiblemonitor.device.runtime.AcquisitionSettings;
import com.mbj.jdcompatiblemonitor.device.runtime.DeviceSchedule;
import com.mbj.jdcompatiblemonitor.device.runtime.DeviceStatus;
import com.mbj.jdcompatiblemonitor.device.runtime.ReadingSink;
import com.mbj.jdcompatiblemonitor.protocol.FrameSummary;
import com.mbj.jdcompatiblemonitor.protocol.FrameTrace;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.runtime.cluster.ClusterLeaderService;
import top.continew.admin.monitor.runtime.cluster.ClusterProperties;
import top.continew.admin.monitor.runtime.config.AcquisitionProperties;
import top.continew.admin.monitor.runtime.model.AcquisitionStatusResp;
import top.continew.admin.monitor.runtime.model.DeviceTestResp;
import top.continew.admin.monitor.runtime.model.TransactResp;
import top.continew.admin.monitor.runtime.service.AcquisitionConfigLoader;
import top.continew.admin.monitor.runtime.service.AcquisitionTopology;
import top.continew.admin.monitor.runtime.service.RuntimeSettingsService;
import top.continew.admin.monitor.runtime.session.ChannelBinding;
import top.continew.admin.monitor.runtime.session.DeviceSession;
import top.continew.admin.monitor.runtime.session.PortSession;
import top.continew.admin.monitor.runtime.signal.SignalRecorder;
import top.continew.starter.core.util.validation.CheckUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 采集调度内核（FR-ACQ-01~04/08/09/10/11、FR-PROTO-07）。
 *
 * <p>调度模型：一个 200ms 的调度线程按端口派发「轮询轮次」，
 * 每个端口由一条专属工作线程串行执行（一条 485 总线同时只能有一次收发），
 * 端口之间互不阻塞（FR-DEV-06）。单个设备失败只影响自身熔断状态，
 * 不会中断同端口其他设备（NFR-REL-03 故障隔离）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcquisitionEngine {

    private final AcquisitionConfigLoader configLoader;
    private final RuntimeSettingsService runtimeSettingsService;
    private final AcquisitionProperties properties;
    private final SignalRecorder signalRecorder;
    private final AcquisitionDiagnostics diagnostics;
    private final ObjectProvider<ReadingSink> readingSinkProvider;
    private final ClusterProperties clusterProperties;
    private final ClusterLeaderService clusterLeaderService;

    private final ConcurrentMap<Long, PortSession> ports = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, DeviceSession> devices = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ExecutorService> portWorkers = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicLong roundCount = new AtomicLong(0L);
    private final AtomicLong devicePollCount = new AtomicLong(0L);
    private final AtomicLong sampleCount = new AtomicLong(0L);

    private volatile List<ReadingSink> readingSinks = List.of();
    private volatile long startedAt;
    private volatile long lastReloadTime;
    private volatile String lastError;
    private volatile ScheduledExecutorService ticker;

    /**
     * 应用就绪后加载配置并按需启动采集
     *
     * <p>放在 {@link ApplicationReadyEvent} 而非 {@code @PostConstruct}，
     * 以确保 Liquibase 已完成业务表迁移。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        this.reload();
        if (clusterProperties.isEnabled() && !clusterLeaderService.isLeader()) {
            log.info("集群模式：本实例为从节点，采集不启动（由主节点采集，本实例仍可查询与配置）");
            return;
        }
        if (properties.isAutoStart()) {
            this.start();
        }
    }

    /**
     * 停止采集并释放所有通道
     */
    @PreDestroy
    public void shutdown() {
        this.stop();
        this.closeAll();
    }

    /**
     * 重新加载配置（先停采集、再释放通道、最后重建）
     */
    public synchronized void reload() {
        boolean wasRunning = running.get();
        this.stopInternal();
        this.closeAll();
        AcquisitionTopology topology = configLoader.load();
        for (PortSession port : topology.ports()) {
            Long portId = port.port().getId();
            ports.put(portId, port);
            portWorkers.put(portId, Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "acq-port-" + port.port().getName());
                thread.setDaemon(true);
                return thread;
            }));
            for (DeviceSession device : port.devices()) {
                devices.put(device.deviceId(), device);
            }
        }
        readingSinks = readingSinkProvider.orderedStream().toList();
        lastReloadTime = System.currentTimeMillis();
        if (wasRunning) {
            this.startInternal();
        }
    }

    /**
     * 开始采集
     */
    public synchronized void start() {
        if (clusterProperties.isEnabled() && !clusterLeaderService.isLeader()) {
            log.warn("集群模式：本实例为从节点，拒绝启动采集（避免与主节点重复采集）");
            return;
        }
        if (running.get()) {
            return;
        }
        this.startInternal();
    }

    /**
     * 停止采集（保留配置与连接）
     */
    public synchronized void stop() {
        this.stopInternal();
    }

    /**
     * 暂停采集：全局或指定端口（FR-ACQ-08）
     *
     * @param portId 端口 ID，为空表示全局暂停
     */
    public void pause(Long portId) {
        if (portId == null) {
            paused.set(true);
            return;
        }
        PortSession port = ports.get(portId);
        if (port != null) {
            port.getPaused().set(true);
        }
    }

    /**
     * 恢复采集：全局或指定端口
     *
     * @param portId 端口 ID，为空表示全局恢复
     */
    public void resume(Long portId) {
        if (portId == null) {
            paused.set(false);
            return;
        }
        PortSession port = ports.get(portId);
        if (port != null) {
            port.getPaused().set(false);
        }
    }

    /**
     * 取设备所属端口会话（继电器控制复用采集链路，避免重复打开同一个串口）
     *
     * @param deviceId 设备 ID
     * @return 端口会话，设备未加载时返回 null
     */
    public PortSession sessionOf(Long deviceId) {
        if (deviceId == null) {
            return null;
        }
        DeviceSession device = devices.get(deviceId);
        if (device == null || device.getDevice().getPortId() == null) {
            return null;
        }
        return ports.get(device.getDevice().getPortId());
    }

    /**
     * 取端口会话（串口自动探测复用正在采集的链路，避免重复打开同一个串口）
     *
     * @param portId 端口 ID
     * @return 端口会话，端口未加载时返回 null
     */
    public PortSession sessionOfPort(Long portId) {
        return portId == null ? null : ports.get(portId);
    }

    /**
     * 自定义帧透传（FR-PROTO-08）
     *
     * <p>直接复用设备所属端口会话发送原始帧，并把请求/应答写入信令留痕；
     * 用于厂商私有指令、参数读写等标准 Modbus 覆盖不到的场景。</p>
     *
     * @param deviceId       设备 ID
     * @param frameHex       待发送帧（十六进制）
     * @param expectedLength 期望应答字节数（0 或空表示不读取）
     * @return 透传结果
     */
    public TransactResp transact(Long deviceId, String frameHex, Integer expectedLength) {
        DeviceSession device = devices.get(deviceId);
        CheckUtils.throwIf(device == null, "设备未加载（可能已停用或不存在）");
        PortSession port = this.findPort(device);
        CheckUtils.throwIf(port == null, "未找到设备所属端口会话");
        byte[] frame = Hex.fromHex(frameHex);
        CheckUtils.throwIf(frame.length == 0, "自定义帧不能为空");
        TransactResp resp = new TransactResp();
        resp.setDeviceId(deviceId);
        resp.setDeviceName(device.getDevice().getName());
        resp.setPortName(port.port().getPortName());
        resp.setRequestHex(FrameSummary.toHex(frame));
        long start = System.currentTimeMillis();
        try {
            byte[] response = port.transact(frame, expectedLength == null ? 0 : expectedLength);
            resp.setResponseHex(response.length == 0 ? null : FrameSummary.toHex(response));
            resp.setResponseLength(response.length);
            resp.setSuccess(true);
        } catch (Exception e) {
            resp.setSuccess(false);
            resp.setErrorMsg(e.getMessage());
            log.warn("自定义帧透传失败（设备 {}）：{}", device.getDevice().getName(), e.getMessage());
        }
        resp.setDurationMs(System.currentTimeMillis() - start);
        for (FrameTrace trace : port.drainTraces()) {
            resp.getFrames()
                .add(diagnostics.toFrame(port, device, trace, (int) (long) resp.getDurationMs()));
        }
        return resp;
    }

    /**
     * 是否正在采集
     *
     * @return 是否正在采集
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 组装运行状态快照
     *
     * @return 状态快照
     */
    public AcquisitionStatusResp snapshot() {
        return diagnostics
            .snapshot(new AcquisitionDiagnostics.SnapshotState(running.get(), paused.get(),
                startedAt == 0 ? null : startedAt, lastReloadTime == 0 ? null : lastReloadTime,
                roundCount
                    .get(),
                devicePollCount.get(), sampleCount.get(), lastError, runtimeSettingsService
                    .current(),
                ports.values()));
    }

    /**
     * 单设备通讯测试（FR-DEV-13）
     *
     * @param deviceId 设备 ID
     * @return 测试结果
     */
    public DeviceTestResp testDevice(Long deviceId) {
        DeviceTestResp resp = new DeviceTestResp();
        resp.setDeviceId(deviceId);
        DeviceSession device = devices.get(deviceId);
        if (device == null) {
            resp.setSuccess(false);
            resp.setErrorMsg("设备未加载（可能已停用或不存在）");
            return resp;
        }
        PortSession port = this.findPort(device);
        if (port == null) {
            resp.setSuccess(false);
            resp.setErrorMsg("未找到设备所属端口会话");
            return resp;
        }
        return diagnostics.testDevice(deviceId, device, port);
    }

    /**
     * 派发一轮调度
     */
    private void tick() {
        try {
            signalRecorder.flushIfDue();
            if (!running.get()) {
                return;
            }
            AcquisitionSettings settings = runtimeSettingsService.current();
            int hour = LocalTime.now().getHour();
            for (PortSession port : ports.values()) {
                if (paused.get() || port.getPaused().get()) {
                    continue;
                }
                ExecutorService worker = portWorkers.get(port.port().getId());
                if (worker == null || !port.getBusy().compareAndSet(false, true)) {
                    continue;
                }
                try {
                    worker.execute(() -> {
                        try {
                            this.pollRound(port, settings, hour);
                        } finally {
                            port.getBusy().set(false);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    port.getBusy().set(false);
                }
            }
        } catch (Exception e) {
            lastError = e.getMessage();
            log.error("采集调度异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 执行一个端口的一轮轮询
     *
     * @param port     端口会话
     * @param settings 采集参数
     * @param hour     当前小时（夜间降频用）
     */
    private void pollRound(PortSession port, AcquisitionSettings settings, int hour) {
        roundCount.incrementAndGet();
        for (DeviceSession device : port.devices()) {
            if (!running.get() || paused.get() || port.getPaused().get()) {
                return;
            }
            if (!device.isActive()) {
                device.refreshStatus();
                continue;
            }
            long now = System.currentTimeMillis();
            int interval = settings.applyTimeOfDay(settings
                .resolveInterval(port.port().getIntervalMs(), device.getDevice().getIntervalMs()),
                hour);
            interval = device.getBreaker().backoffInterval(interval);
            if (!DeviceSchedule.isDue(device.getLastPollTime().get(), now, interval)) {
                continue;
            }
            if (!device.getBreaker().allowRequest(now)) {
                device.refreshStatus();
                continue;
            }
            this.pollDevice(port, device, settings);
        }
    }

    /**
     * 采集一台设备的全部通道
     *
     * @param port     端口会话
     * @param device   设备会话
     * @param settings 采集参数
     */
    private void pollDevice(PortSession port, DeviceSession device, AcquisitionSettings settings) {
        long start = System.currentTimeMillis();
        int timeout = settings.resolveTimeout(device.getDevice().getTimeoutMs());
        long budgetDeadline = start + settings.pollBudgetMs();
        device.getMetrics().recordPoll(start);
        device.markPolled(start);
        device.getBudgetExceeded().set(false);
        String error = null;
        int frames = 0;
        // 本轮是否被预算/报文数上限截断：截断时不能按「全通道成功」记账
        boolean truncated = false;
        try {
            for (ChannelBinding binding : device.getChannels()) {
                if (System.currentTimeMillis() > budgetDeadline) {
                    device.markBudgetExceeded();
                    log.warn("设备 [{}] 超出轮询预算 {}ms，本轮跳过剩余通道", device.getDevice().getName(),
                        settings.pollBudgetMs());
                    truncated = true;
                    break;
                }
                if (frames++ >= properties.getMaxFramesPerDevice()) {
                    log.warn("设备 [{}] 单轮报文数超过上限 {}，本轮提前结束", device.getDevice().getName(),
                        properties.getMaxFramesPerDevice());
                    truncated = true;
                    break;
                }
                int[] registers = port.readHoldingRegisters(device.slaveAddr(), binding.regAddr(),
                    binding.registerCount(), timeout);
                for (AnalogSample sample : ChannelValueReader.readSamples(binding.config(),
                    registers)) {
                    this.publish(port, device, binding, sample);
                }
            }
            if (truncated) {
                // 预算/报文数超限属于本轮降级：剩余通道这轮没读到，不能记「全通道成功」，
                // 否则指标永远 100% 成功、熔断永不触发，而剩余通道其实一直停更。
                device.getBreaker().recordFailure(System.currentTimeMillis());
                device.getMetrics().recordFailure(System.currentTimeMillis(),
                    System.currentTimeMillis() - start, false, "超出轮询预算或单轮报文数上限，本轮未读完所有通道");
            } else {
                device.getBreaker().recordSuccess();
                device.getMetrics().recordSuccess(System.currentTimeMillis(),
                    System.currentTimeMillis() - start);
            }
            device.refreshStatus();
            this.notifyState(port, device, DeviceStatus.ONLINE);
        } catch (Exception e) {
            error = e.getMessage();
            device.getBreaker().recordFailure(System.currentTimeMillis());
            device.getMetrics().recordFailure(System.currentTimeMillis(),
                System.currentTimeMillis() - start, AcquisitionElements.isTimeout(e), error);
            lastError = error;
            if (log.isDebugEnabled()) {
                log.debug("设备 [{}] 采集失败：{}", device.getDevice().getName(), error);
            }
        } finally {
            devicePollCount.incrementAndGet();
            device.refreshStatus();
            if (error != null) {
                this.notifyState(port, device, device.getStatus());
            }
            signalRecorder.collect(port, device, start, error);
        }
    }

    /**
     * 把设备状态通知给所有消费者（FR-WEB-03）
     *
     * @param port   端口会话
     * @param device 设备会话
     * @param status 设备状态
     */
    private void notifyState(PortSession port, DeviceSession device, DeviceStatus status) {
        List<ReadingSink> sinks = readingSinks;
        for (ReadingSink sink : sinks) {
            try {
                sink.onDeviceState(port.port().getId(), device.deviceId(), status);
            } catch (Exception e) {
                log.error("设备状态同步失败：{}", e.getMessage());
            }
        }
    }

    /**
     * 推送一条采集样本给所有消费者
     *
     * @param port    端口会话
     * @param device  设备会话
     * @param binding 通道绑定
     * @param sample  模拟量样本
     */
    private void publish(PortSession port, DeviceSession device, ChannelBinding binding,
        AnalogSample sample) {
        sampleCount.incrementAndGet();
        List<ReadingSink> sinks = readingSinks;
        if (sinks.isEmpty()) {
            return;
        }
        AnalogDefinition definition = AcquisitionElements.analog(binding, sample.analogNo());
        AcquisitionSample payload = new AcquisitionSample(port.port().getId(),
            port.port().getPortName(), device.deviceId(), device.getDevice().getName(),
            device.slaveAddr(), sample.channelNo(), sample.analogNo(),
            AcquisitionElements.elementType(binding, sample.analogNo()),
            AcquisitionElements.elementName(binding, sample.analogNo()), sample.unit(), sample
                .rawValue(),
            sample.engineeringValue(), System.currentTimeMillis(), definition.lowerLimit(),
            definition.upperLimit());
        for (ReadingSink sink : sinks) {
            try {
                sink.accept(payload);
            } catch (Exception e) {
                log.error("采集结果消费失败：{}", e.getMessage());
            }
        }
    }

    /**
     * 查找设备所属端口会话
     *
     * @param device 设备会话
     * @return 端口会话，未找到返回 null
     */
    private PortSession findPort(DeviceSession device) {
        Long portId = device.getDevice().getPortId();
        return portId == null ? null : ports.get(portId);
    }

    /**
     * 启动调度线程
     */
    private void startInternal() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        startedAt = System.currentTimeMillis();
        long tick = Math.max(properties.getTickMs(), 50L);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "acq-ticker");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, tick, tick, TimeUnit.MILLISECONDS);
        this.ticker = executor;
        log.info("采集调度已启动：端口 {} 个，设备 {} 台，调度周期 {}ms", ports.size(), devices.size(),
            tick);
    }

    /**
     * 停止调度线程
     */
    private void stopInternal() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        ScheduledExecutorService executor = ticker;
        ticker = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        log.info("采集调度已停止");
    }

    /**
     * 关闭全部端口与工作线程
     */
    private void closeAll() {
        for (PortSession port : ports.values()) {
            port.close();
        }
        ports.clear();
        devices.clear();
        for (Map.Entry<Long, ExecutorService> entry : portWorkers.entrySet()) {
            entry.getValue().shutdownNow();
        }
        portWorkers.clear();
    }
}
