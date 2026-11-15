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

package top.continew.admin.monitor.runtime.timesync;

import com.mbj.jdcompatiblemonitor.protocol.ModbusTimeCodec;
import com.mbj.jdcompatiblemonitor.protocol.Protocols;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizPortDO;
import top.continew.admin.monitor.runtime.model.TimeSyncResultResp;
import top.continew.admin.monitor.runtime.model.TimeSyncStatusResp;
import top.continew.admin.monitor.runtime.service.RuntimeSettingsService;
import top.continew.admin.monitor.runtime.session.DeviceSessionSupport;
import top.continew.starter.core.util.validation.CheckUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备校时（NFR-REL-06：偏差可检测、可选择性校时）。
 *
 * <p>按标准 ModBus 规约：寄存器 684~689 依次为 年、月、日、时、分、秒，
 * 读取用 03、写入用 16（{@link ModbusTimeCodec}）。两种用法：</p>
 * <ul>
 *     <li>手动：页面一键把平台本机时间同步到指定设备或全部设备；</li>
 *     <li>自动：定时任务按计划执行，偏差在容差内则跳过写入，避免无谓的写操作。</li>
 * </ul>
 *
 * <p>写操作受 SYSTEM 配置 {@code TIME_SYNC_WRITE_ENABLED} 约束（FR-PROTO-09）；
 * 仁科 RS-Modbus（附录 B）为只读规约，不参与校时。</p>
 *
 * @author jdrk
 * @since 2026/10/1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTimeSyncService {

    /** 触发方式：手动一键校时 */
    public static final String ACTION_MANUAL = "MANUAL";
    /** 触发方式：自动定时校时 */
    public static final String ACTION_AUTO = "AUTO";
    /** 结果：成功 */
    public static final String RESULT_SUCCESS = "SUCCESS";
    /** 结果：偏差在容差内，跳过写入 */
    public static final String RESULT_SKIPPED = "SKIPPED";
    /** 结果：失败 */
    public static final String RESULT_FAILED = "FAILED";
    /** 结果：被拒绝（写保护关闭或规约不支持校时） */
    public static final String RESULT_REJECTED = "REJECTED";

    /** 回读允许的偏差（秒）：串口一来一回可能跨秒 */
    private static final long READBACK_TOLERANCE_SECONDS = 1L;

    private final BizDeviceMapper bizDeviceMapper;
    private final BizPortMapper bizPortMapper;
    private final RuntimeSettingsService runtimeSettingsService;
    private final DeviceSessionSupport deviceSessionSupport;
    private final TimeSyncProperties timeSyncProperties;

    private volatile LocalDateTime lastRunTime;
    private volatile String lastRunMessage;

    /**
     * 校时写入是否被允许（FR-PROTO-09 写保护）
     *
     * @return 允许返回 true
     */
    public boolean writeEnabled() {
        int config = runtimeSettingsService.systemInt("TIME_SYNC_WRITE_ENABLED", timeSyncProperties
            .isWriteEnabled() ? 1 : 0);
        return config != 0;
    }

    /**
     * 是否启用自动定时校时
     *
     * @return 启用返回 true
     */
    public boolean autoEnabled() {
        int config = runtimeSettingsService.systemInt("TIME_SYNC_AUTO_ENABLED", timeSyncProperties
            .isAutoEnabled() ? 1 : 0);
        return config != 0;
    }

    /**
     * 时间寄存器起始地址
     *
     * @return 起始地址
     */
    public int registerBase() {
        return runtimeSettingsService.systemInt("TIME_SYNC_REG_BASE", timeSyncProperties
            .getRegisterBase());
    }

    /**
     * 偏差容差（秒）
     *
     * @return 容差秒数，最小为 0
     */
    public int toleranceSeconds() {
        int config = runtimeSettingsService.systemInt("TIME_SYNC_TOLERANCE_SECONDS",
            timeSyncProperties.getToleranceSeconds());
        return Math.max(config, 0);
    }

    /**
     * 自动校时计划
     *
     * @return Spring cron 表达式
     */
    public String autoCron() {
        return timeSyncProperties.getAutoCron();
    }

    /**
     * 查询校时状态与各设备时间偏差（NFR-REL-06 偏差可检测）
     *
     * @return 校时状态
     */
    public TimeSyncStatusResp status() {
        TimeSyncStatusResp resp = new TimeSyncStatusResp();
        resp.setServerTime(LocalDateTime.now().withNano(0));
        resp.setWriteEnabled(this.writeEnabled());
        resp.setAutoEnabled(this.autoEnabled());
        resp.setAutoCron(this.autoCron());
        resp.setToleranceSeconds(this.toleranceSeconds());
        resp.setRegisterBase(this.registerBase());
        resp.setLastRunTime(lastRunTime);
        resp.setLastRunMessage(lastRunMessage);
        int tolerance = this.toleranceSeconds();
        for (BizDeviceDO device : this.devices(null)) {
            TimeSyncStatusResp.DeviceItem item = new TimeSyncStatusResp.DeviceItem();
            item.setDeviceId(device.getId());
            item.setDeviceName(device.getName());
            item.setDeviceAddr(device.getSlaveAddr());
            item.setProtocol(device.getProtocol());
            item.setPortName(this.portNameOf(device));
            item.setSupported(supported(device.getProtocol()));
            if (!Boolean.TRUE.equals(item.getSupported())) {
                item.setOnline(false);
                item.setMessage("协议 " + device.getProtocol() + " 不支持校时（仁科 RS-Modbus 为只读规约）");
                resp.getDevices().add(item);
                continue;
            }
            try {
                LocalDateTime deviceTime = this.readDeviceTime(device);
                long offset = Duration.between(LocalDateTime.now().withNano(0), deviceTime)
                    .getSeconds();
                item.setOnline(true);
                item.setDeviceTime(deviceTime);
                item.setOffsetSeconds(offset);
                item.setWithinTolerance(Math.abs(offset) <= tolerance);
            } catch (Exception e) {
                item.setOnline(false);
                item.setMessage("读取设备时间失败：" + e.getMessage());
                log.debug("读取设备时间失败（{}）：{}", device.getName(), e.getMessage());
            }
            resp.getDevices().add(item);
        }
        return resp;
    }

    /**
     * 手动校时：把平台本机时间同步到单台设备（NFR-REL-06 一键同步）
     *
     * @param deviceId 设备 ID
     * @return 校时结果
     */
    public TimeSyncResultResp syncDevice(Long deviceId) {
        BizDeviceDO device = this.requireDevice(deviceId);
        LocalDateTime reference = LocalDateTime.now().withNano(0);
        TimeSyncResultResp resp = this.sync(device, reference, ACTION_MANUAL, TimeSyncMode.ALL);
        this.recordRun(ACTION_MANUAL, List.of(resp));
        return resp;
    }

    /**
     * 手动校时：把平台本机时间同步到全部（或指定）设备（NFR-REL-06 一键同步）
     *
     * @param deviceIds 设备 ID 列表，为空表示全部启用设备
     * @param mode      校时方式，为空按全部下发处理
     * @return 各设备校时结果
     */
    public List<TimeSyncResultResp> syncAll(List<Long> deviceIds, TimeSyncMode mode) {
        return this.run(ACTION_MANUAL, deviceIds, mode == null ? TimeSyncMode.ALL : mode);
    }

    /**
     * 自动校时：按计划执行一轮（偏差在容差内的设备跳过写入）
     *
     * @return 各设备校时结果，未启用自动校时时返回空列表
     */
    public List<TimeSyncResultResp> syncAutomatically() {
        if (!this.autoEnabled()) {
            log.debug("自动定时校时未启用（TIME_SYNC_AUTO_ENABLED=0），本轮跳过");
            return new ArrayList<>();
        }
        return this.run(ACTION_AUTO, null, TimeSyncMode.TOLERANT);
    }

    private List<TimeSyncResultResp> run(String action, List<Long> deviceIds, TimeSyncMode mode) {
        LocalDateTime reference = LocalDateTime.now().withNano(0);
        List<TimeSyncResultResp> results = new ArrayList<>();
        int limit = Math.max(timeSyncProperties.getMaxDevicesPerRun(), 1);
        for (BizDeviceDO device : this.devices(deviceIds)) {
            if (results.size() >= limit) {
                log.warn("单次校时设备数超过上限 {}，其余设备本轮跳过", limit);
                break;
            }
            results.add(this.sync(device, reference, action, mode));
        }
        this.recordRun(action, results);
        return results;
    }

    /**
     * 单设备校时：读偏差 → 必要时写时间 → 回读校验
     *
     * @param device    设备
     * @param reference 下发的平台本机时间
     * @param action    触发方式
     * @param mode      校时方式：仅超差下发时，偏差在容差内的设备跳过写入
     * @return 校时结果
     */
    private TimeSyncResultResp sync(BizDeviceDO device, LocalDateTime reference, String action,
        TimeSyncMode mode) {
        long startedAt = System.currentTimeMillis();
        TimeSyncResultResp resp = new TimeSyncResultResp();
        resp.setDeviceId(device.getId());
        resp.setDeviceName(device.getName());
        resp.setDeviceAddr(device.getSlaveAddr());
        resp.setProtocol(device.getProtocol());
        resp.setPortName(this.portNameOf(device));
        resp.setAction(action);
        resp.setServerTime(reference);
        resp.setWriteSkipped(false);
        resp.setReadbackOk(false);
        if (!supported(device.getProtocol())) {
            return this.finish(resp, RESULT_REJECTED, "协议 " + device.getProtocol()
                + " 不支持校时（仁科 RS-Modbus 为只读规约）", startedAt);
        }
        if (!this.writeEnabled()) {
            return this.finish(resp, RESULT_REJECTED,
                "校时写入已被系统配置禁用（TIME_SYNC_WRITE_ENABLED=0），操作已拒绝", startedAt);
        }
        int tolerance = this.toleranceSeconds();
        try {
            // 设备时钟可能尚未初始化（寄存器为 0）或不可读：只作为参考信息，
            // 读不到不影响下发，否则「给未授时的新设备校时」这一主要场景会做不了
            LocalDateTime deviceTimeBefore = null;
            Long offsetBefore = null;
            try {
                deviceTimeBefore = this.readDeviceTime(device);
                offsetBefore = Duration.between(reference, deviceTimeBefore).getSeconds();
            } catch (Exception e) {
                log.debug("读取校时前设备时间失败（{}）：{}", device.getName(), e.getMessage());
            }
            resp.setDeviceTimeBefore(deviceTimeBefore);
            resp.setOffsetSecondsBefore(offsetBefore);
            if (TimeSyncMode.TOLERANT == mode && offsetBefore != null
                && Math.abs(offsetBefore) <= tolerance) {
                resp.setWriteSkipped(true);
                return this.finish(resp, RESULT_SKIPPED, "偏差 " + offsetBefore + " 秒在容差 "
                    + tolerance + " 秒内，无需校时", startedAt);
            }
            this.writeDeviceTime(device, reference);
            LocalDateTime readback = this.readDeviceTime(device);
            long readbackOffset = Duration.between(reference, readback).getSeconds();
            resp.setReadbackTime(readback);
            resp.setReadbackOffsetSeconds(readbackOffset);
            boolean ok = Math.abs(readbackOffset) <= READBACK_TOLERANCE_SECONDS;
            resp.setReadbackOk(ok);
            String beforeNote = offsetBefore == null ? "（校时前设备时间不可读）" : "（校时前偏差 "
                + offsetBefore + " 秒）";
            return this.finish(resp, ok ? RESULT_SUCCESS : RESULT_FAILED, ok
                ? "校时成功，回读一致" + beforeNote
                : "回读与下发时间不一致（相差 " + readbackOffset + " 秒），请检查设备", startedAt);
        } catch (Exception e) {
            log.warn("设备校时失败（{}）：{}", device.getName(), e.getMessage());
            return this.finish(resp, RESULT_FAILED, "校时失败：" + e.getMessage(), startedAt);
        }
    }

    private TimeSyncResultResp finish(TimeSyncResultResp resp, String status, String message,
        long startedAt) {
        resp.setResultStatus(status);
        resp.setMessage(message);
        resp.setDurationMs(System.currentTimeMillis() - startedAt);
        return resp;
    }

    private void recordRun(String action, List<TimeSyncResultResp> results) {
        long success = results.stream().filter(item -> RESULT_SUCCESS.equals(item
            .getResultStatus())).count();
        long skipped = results.stream().filter(item -> RESULT_SKIPPED.equals(item
            .getResultStatus())).count();
        long failed = results.stream().filter(item -> RESULT_FAILED.equals(item
            .getResultStatus())).count();
        long rejected = results.stream().filter(item -> RESULT_REJECTED.equals(item
            .getResultStatus())).count();
        lastRunTime = LocalDateTime.now().withNano(0);
        lastRunMessage = (ACTION_AUTO.equals(action) ? "自动校时" : "手动校时") + "：共 " + results
            .size() + " 台，成功 " + success + "，跳过 " + skipped + "，失败 " + failed + "，拒绝 "
            + rejected;
        log.info("设备校时结束（{}）", lastRunMessage);
    }

    private LocalDateTime readDeviceTime(BizDeviceDO device) {
        int base = this.registerBase();
        int[] registers = deviceSessionSupport.usingSession(device, session -> session
            .readHoldingRegisters(device.getSlaveAddr(), base, ModbusTimeCodec.REGISTER_COUNT,
                deviceSessionSupport.timeout(device)));
        return ModbusTimeCodec.decode(registers);
    }

    private void writeDeviceTime(BizDeviceDO device, LocalDateTime time) {
        int base = this.registerBase();
        int[] values = ModbusTimeCodec.encode(time);
        deviceSessionSupport.usingSession(device, session -> {
            session.writeMultipleRegisters(device.getSlaveAddr(), base, values, deviceSessionSupport
                .timeout(device));
            return Boolean.TRUE;
        });
    }

    private BizDeviceDO requireDevice(Long deviceId) {
        CheckUtils.throwIf(deviceId == null, "设备 ID 不能为空");
        BizDeviceDO device = bizDeviceMapper.selectById(deviceId);
        CheckUtils.throwIf(device == null, "设备不存在");
        CheckUtils.throwIf(device.getPortId() == null, "设备未配置所属端口");
        return device;
    }

    /**
     * 取参与校时的设备（默认仅启用设备，按端口与排序号稳定输出）
     *
     * @param deviceIds 指定设备 ID，为空表示全部
     * @return 设备列表
     */
    private List<BizDeviceDO> devices(List<Long> deviceIds) {
        if (deviceIds != null && !deviceIds.isEmpty()) {
            return bizDeviceMapper.lambdaQuery().in(BizDeviceDO::getId, deviceIds)
                .eq(BizDeviceDO::getEnabled,
                    true)
                .orderByAsc(BizDeviceDO::getPortId).orderByAsc(BizDeviceDO::getSortNo).list();
        }
        return bizDeviceMapper.lambdaQuery().eq(BizDeviceDO::getEnabled, true)
            .orderByAsc(BizDeviceDO::getPortId)
            .orderByAsc(BizDeviceDO::getSortNo).list();
    }

    private String portNameOf(BizDeviceDO device) {
        BizPortDO port = device.getPortId() == null ? null : bizPortMapper.selectById(device
            .getPortId());
        return port == null ? null : port.getPortName();
    }

    /**
     * 协议是否支持校时：仁科 RS-Modbus 寄存器语义不同且只读（附录 B）
     *
     * @param protocol 协议
     * @return 支持返回 true
     */
    private static boolean supported(String protocol) {
        return protocol != null && !Protocols.RS_MODBUS.equalsIgnoreCase(protocol.trim());
    }
}
