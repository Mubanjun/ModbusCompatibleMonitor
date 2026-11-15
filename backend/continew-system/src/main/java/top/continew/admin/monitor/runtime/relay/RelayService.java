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

package top.continew.admin.monitor.runtime.relay;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizPortDO;
import top.continew.admin.monitor.model.entity.BizRelayLogDO;
import top.continew.admin.monitor.runtime.model.RelayControlResp;
import top.continew.admin.monitor.runtime.model.RelayStatusResp;
import top.continew.admin.monitor.runtime.service.RuntimeSettingsService;
import top.continew.admin.monitor.runtime.session.DeviceSessionSupport;
import top.continew.admin.monitor.service.BizRelayLogService;
import top.continew.starter.core.util.validation.CheckUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 继电器控制（FR-RLY-01~05）。
 *
 * <p>按标准 Modbus 规约：寄存器 696~703 对应 1~8 号继电器，0 断开、1 吸合，
 * 读取用 03、写入用 06/16。控制链路复用采集端口会话（避免重复打开同一个串口），
 * 设备未加载时临时建立连接。</p>
 *
 * <p>每次操作都会写入 {@code biz_relay_log}：记录操作人、设备、继电器、动作、
 * 操作前后状态、回读结果与耗时（FR-RLY-04）；写入受 SYSTEM 配置
 * {@code RELAY_WRITE_ENABLED} 约束（FR-PROTO-09）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelayService {

    /** 动作：闭合 */
    public static final String ACTION_CLOSE = "CLOSE";
    /** 动作：断开 */
    public static final String ACTION_OPEN = "OPEN";
    /** 动作：批量 */
    public static final String ACTION_BATCH = "BATCH";
    /** 动作：定时恢复 */
    public static final String ACTION_AUTO_RESTORE = "AUTO_RESTORE";
    /** 结果：成功 */
    public static final String RESULT_SUCCESS = "SUCCESS";
    /** 结果：失败 */
    public static final String RESULT_FAILED = "FAILED";
    /** 结果：被拒绝 */
    public static final String RESULT_REJECTED = "REJECTED";

    private final BizDeviceMapper bizDeviceMapper;
    private final BizPortMapper bizPortMapper;
    private final BizRelayLogService bizRelayLogService;
    private final RuntimeSettingsService runtimeSettingsService;
    private final RelayProperties relayProperties;
    private final DeviceSessionSupport deviceSessionSupport;

    private volatile ScheduledExecutorService restorer;

    /**
     * 释放定时恢复线程
     */
    @PreDestroy
    public void shutdown() {
        ScheduledExecutorService executor = restorer;
        restorer = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * 查询继电器状态（FR-RLY-01）
     *
     * @param deviceId 设备 ID
     * @return 状态
     */
    public RelayStatusResp status(Long deviceId) {
        BizDeviceDO device = this.requireDevice(deviceId);
        RelayStatusResp resp = new RelayStatusResp();
        resp.setDeviceId(device.getId());
        resp.setDeviceName(device.getName());
        resp.setDeviceAddr(device.getSlaveAddr());
        resp.setRelayRegBase(device.getRelayRegBase());
        resp.setRelayCount(device.getRelayCount());
        BizPortDO port = bizPortMapper.selectById(device.getPortId());
        resp.setPortName(port == null ? null : port.getPortName());
        try {
            int[] registers = this.readRelays(device);
            resp.setOnline(true);
            resp.setRegisters(this.toList(registers));
            resp.setRelays(this.toItems(device, registers));
        } catch (Exception e) {
            resp.setOnline(false);
            resp.setErrorMsg(e.getMessage());
            log.warn("读取继电器状态失败（设备 {}）：{}", device.getName(), e.getMessage());
        }
        return resp;
    }

    /**
     * 单路继电器控制（FR-RLY-02）
     *
     * @param deviceId 设备 ID
     * @param relayNo  继电器序号（1 起）
     * @param closed   是否吸合
     * @param durationMs 执行时长（毫秒，0 表示保持）
     * @return 操作结果
     */
    public RelayControlResp control(Long deviceId, Integer relayNo, Boolean closed,
        Integer durationMs) {
        BizDeviceDO device = this.requireDevice(deviceId);
        int index = this.validateRelayNo(device, relayNo);
        int duration = durationMs == null ? 0 : Math.max(durationMs, 0);
        RelayControlResp resp = this.newResult(device, relayNo, closed ? 1 : 0, duration);
        if (!this.writeEnabled()) {
            return this.reject(device, resp, ACTION_CLOSE, "继电器写入已被系统配置禁用"
                + "（RELAY_WRITE_ENABLED=0），操作已拒绝");
        }
        int[] before = null;
        try {
            before = this.readRelays(device);
            this.writeRelay(device, index, closed ? 1 : 0);
            int[] after = this.readRelays(device);
            resp.setRelays(this.toItems(device, after));
            resp.setPreviousValue(String.valueOf(before[index]));
            resp.setReadbackValue(String.valueOf(after[index]));
            boolean ok = after[index] == (closed ? 1 : 0);
            resp.setReadbackOk(ok);
            resp.setSuccess(ok);
            resp.setMessage(ok ? "操作成功，回读一致" : "回读与请求不一致，请检查设备状态");
            resp.setLogId(this.log(device, relayNo, closed ? ACTION_CLOSE : ACTION_OPEN, resp,
                duration, ok ? RESULT_SUCCESS : RESULT_FAILED, ok ? null : "回读不一致"));
            if (ok && duration > 0 && before != null) {
                this.scheduleRestore(device, index, before[index], duration);
            }
        } catch (Exception e) {
            resp.setSuccess(false);
            resp.setReadbackOk(false);
            resp.setMessage("操作失败：" + e.getMessage());
            resp.setLogId(this.log(device, relayNo, closed ? ACTION_CLOSE : ACTION_OPEN, resp,
                duration, RESULT_FAILED, e.getMessage()));
            log.warn("继电器操作失败（设备 {}，第 {} 路）：{}", device.getName(), relayNo, e
                .getMessage());
        }
        return resp;
    }

    /**
     * 批量继电器控制（FR-RLY-03）
     *
     * @param deviceId 设备 ID
     * @param values   各路目标状态（按序号顺序）
     * @return 操作结果
     */
    public RelayControlResp batch(Long deviceId, List<Integer> values) {
        BizDeviceDO device = this.requireDevice(deviceId);
        CheckUtils.throwIf(values == null || values.isEmpty(), "批量控制值不能为空");
        int count = Math.min(values.size(), device.getRelayCount());
        RelayControlResp resp = this.newResult(device, null, null, 0);
        resp.setRequestValue(this.join(values.subList(0, count)));
        if (!this.writeEnabled()) {
            return this.reject(device, resp, ACTION_BATCH, "继电器写入已被系统配置禁用"
                + "（RELAY_WRITE_ENABLED=0），操作已拒绝");
        }
        try {
            int[] before = this.readRelays(device);
            resp.setPreviousValue(this.join(this.toList(before)));
            int[] payload = new int[count];
            for (int i = 0; i < count; i++) {
                Integer value = values.get(i);
                payload[i] = (value != null && value == 1) ? 1 : 0;
            }
            int base = device.getRelayRegBase();
            deviceSessionSupport.usingSession(device, session -> {
                session.writeMultipleRegisters(device.getSlaveAddr(), base, payload,
                    deviceSessionSupport
                        .timeout(device));
                return Boolean.TRUE;
            });
            int[] after = this.readRelays(device);
            resp.setReadbackValue(this.join(this.toList(after)));
            resp.setRelays(this.toItems(device, after));
            boolean ok = true;
            for (int i = 0; i < count; i++) {
                if (after[i] != payload[i]) {
                    ok = false;
                    break;
                }
            }
            resp.setReadbackOk(ok);
            resp.setSuccess(ok);
            resp.setMessage(ok ? "批量操作成功，回读一致" : "回读与请求不一致，请检查设备状态");
            resp.setLogId(this.log(device, null, ACTION_BATCH, resp, 0, ok ? RESULT_SUCCESS
                : RESULT_FAILED, ok ? null : "回读不一致"));
        } catch (Exception e) {
            resp.setSuccess(false);
            resp.setReadbackOk(false);
            resp.setMessage("批量操作失败：" + e.getMessage());
            resp.setLogId(this.log(device, null, ACTION_BATCH, resp, 0, RESULT_FAILED, e
                .getMessage()));
        }
        return resp;
    }

    /**
     * 继电器写入是否被允许（FR-PROTO-09）
     *
     * @return 允许返回 true
     */
    public boolean writeEnabled() {
        int config = runtimeSettingsService.systemInt("RELAY_WRITE_ENABLED", relayProperties
            .isWriteEnabled() ? 1 : 0);
        return config != 0;
    }

    private BizDeviceDO requireDevice(Long deviceId) {
        CheckUtils.throwIf(deviceId == null, "设备 ID 不能为空");
        BizDeviceDO device = bizDeviceMapper.selectById(deviceId);
        CheckUtils.throwIf(device == null, "设备不存在");
        CheckUtils.throwIf(device.getPortId() == null, "设备未配置所属端口");
        CheckUtils.throwIf(device.getRelayRegBase() == null || device.getRelayRegBase() <= 0,
            "设备未配置继电器寄存器起始地址");
        CheckUtils.throwIf(device.getRelayCount() == null || device.getRelayCount() <= 0,
            "设备未配置继电器路数");
        return device;
    }

    private int validateRelayNo(BizDeviceDO device, Integer relayNo) {
        CheckUtils.throwIf(relayNo == null, "继电器序号不能为空");
        CheckUtils.throwIf(relayNo < 1 || relayNo > device.getRelayCount(), "继电器序号超出范围"
            + "（1~{}）", device.getRelayCount());
        return relayNo - 1;
    }

    private int[] readRelays(BizDeviceDO device) {
        int count = device.getRelayCount();
        return deviceSessionSupport.usingSession(device,
            session -> session.readHoldingRegisters(device
                .getSlaveAddr(), device.getRelayRegBase(), count,
                deviceSessionSupport.timeout(device)));
    }

    private void writeRelay(BizDeviceDO device, int index, int value) {
        deviceSessionSupport.usingSession(device, session -> {
            session.writeSingleRegister(device.getSlaveAddr(), device.getRelayRegBase() + index,
                value, deviceSessionSupport.timeout(device));
            return Boolean.TRUE;
        });
    }

    /**
     * 定时恢复：到时把继电器写回操作前的状态（FR-RLY-02 执行时长）
     *
     * @param device   设备
     * @param index    继电器下标
     * @param previous 操作前的值
     * @param delayMs  时长
     */
    private void scheduleRestore(BizDeviceDO device, int index, int previous, int delayMs) {
        int capped = Math.min(delayMs, Math.max(relayProperties.getMaxDurationMs(), 1000));
        this.executor().schedule(() -> {
            // 定时恢复由调度线程执行，不属于任何人工会话：清掉可能被继承的用户上下文，
            // 审计记录里不能出现“操作人”（FR-RLY-04）
            UserContextHolder.clearContext();
            RelayControlResp resp = this.newResult(device, index + 1, previous, 0);
            try {
                this.writeRelay(device, index, previous);
                int[] after = this.readRelays(device);
                resp.setReadbackValue(String.valueOf(after[index]));
                boolean ok = after[index] == previous;
                resp.setReadbackOk(ok);
                resp.setSuccess(ok);
                resp.setMessage(ok ? "定时恢复成功" : "定时恢复回读不一致");
                this.log(device, index + 1, ACTION_AUTO_RESTORE, resp, capped, ok ? RESULT_SUCCESS
                    : RESULT_FAILED, ok ? null : "回读不一致", null, null);
            } catch (Exception e) {
                resp.setSuccess(false);
                resp.setMessage("定时恢复失败：" + e.getMessage());
                this.log(device, index + 1, ACTION_AUTO_RESTORE, resp, capped, RESULT_FAILED, e
                    .getMessage(), null, null);
                log.warn("继电器定时恢复失败（设备 {}）：{}", device.getName(), e.getMessage());
            }
        }, capped, TimeUnit.MILLISECONDS);
    }

    private ScheduledExecutorService executor() {
        ScheduledExecutorService local = restorer;
        if (local == null) {
            synchronized (this) {
                if (restorer == null) {
                    restorer = Executors.newSingleThreadScheduledExecutor(task -> {
                        Thread thread = new Thread(task, "acq-relay-restore");
                        thread.setDaemon(true);
                        return thread;
                    });
                }
                local = restorer;
            }
        }
        return local;
    }

    private RelayControlResp newResult(BizDeviceDO device, Integer relayNo, Integer requestValue,
        int durationMs) {
        RelayControlResp resp = new RelayControlResp();
        resp.setDeviceId(device.getId());
        resp.setDeviceName(device.getName());
        resp.setRelayNo(relayNo);
        resp.setRequestValue(requestValue == null ? null : String.valueOf(requestValue));
        resp.setDurationMs(durationMs);
        resp.setOperateTime(LocalDateTime.now());
        return resp;
    }

    private RelayControlResp reject(BizDeviceDO device, RelayControlResp resp, String action,
        String reason) {
        resp.setSuccess(false);
        resp.setReadbackOk(false);
        resp.setMessage(reason);
        resp.setLogId(this.log(device, resp.getRelayNo(), action, resp, resp.getDurationMs() == null
            ? 0
            : resp.getDurationMs(), RESULT_REJECTED, reason));
        return resp;
    }

    private Long log(BizDeviceDO device, Integer relayNo, String action, RelayControlResp resp,
        int durationMs, String status, String error) {
        return this.log(device, relayNo, action, resp, durationMs, status, error, UserContextHolder
            .getUserId(), UserContextHolder.getUsername());
    }

    /**
     * 写继电器操作审计记录（可显式指定操作人，系统自动恢复传 null）
     *
     * @param device       设备
     * @param relayNo      继电器序号
     * @param action       动作
     * @param resp         操作结果
     * @param durationMs   执行时长
     * @param status       结果状态
     * @param error        失败原因
     * @param operatorId   操作人 ID
     * @param operatorName 操作人名称
     * @return 审计记录 ID
     */
    private Long log(BizDeviceDO device, Integer relayNo, String action, RelayControlResp resp,
        int durationMs, String status, String error, Long operatorId, String operatorName) {
        try {
            BizRelayLogDO entity = new BizRelayLogDO();
            entity.setCreateUser(0L);
            entity.setDeviceId(device.getId());
            entity.setDeviceName(device.getName());
            entity.setDeviceAddr(device.getSlaveAddr());
            entity.setPortId(device.getPortId());
            entity.setRelayNo(relayNo);
            entity.setAction(action);
            entity.setRequestValue(resp.getRequestValue());
            entity.setPreviousValue(resp.getPreviousValue());
            entity.setReadbackValue(resp.getReadbackValue());
            entity.setReadbackOk(Boolean.TRUE.equals(resp.getReadbackOk()));
            entity.setDurationMs(durationMs);
            entity.setResultStatus(status);
            entity.setErrorMsg(error);
            entity.setOperateTime(LocalDateTime.now());
            entity.setOperatorId(operatorId);
            entity.setOperatorName(operatorName);
            bizRelayLogService.save(entity);
            return entity.getId();
        } catch (Exception e) {
            log.error("写继电器操作日志失败：{}", e.getMessage());
            return null;
        }
    }

    private List<RelayStatusResp.RelayItem> toItems(BizDeviceDO device, int[] registers) {
        List<RelayStatusResp.RelayItem> items = new ArrayList<>();
        for (int i = 0; i < registers.length; i++) {
            RelayStatusResp.RelayItem item = new RelayStatusResp.RelayItem();
            item.setRelayNo(i + 1);
            item.setRegister(device.getRelayRegBase() + i);
            item.setClosed(registers[i] == 1);
            item.setLabel(registers[i] == 1 ? "吸合" : "断开");
            items.add(item);
        }
        return items;
    }

    private List<Integer> toList(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int value : values) {
            list.add(value);
        }
        return list;
    }

    private String join(List<Integer> values) {
        StringBuilder builder = new StringBuilder();
        for (Integer value : values) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value);
        }
        return builder.toString();
    }

}
