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

import com.mbj.jdcompatiblemonitor.protocol.DeviceProtocol;
import com.mbj.jdcompatiblemonitor.protocol.Protocols;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.model.entity.BizPortDO;
import top.continew.admin.monitor.runtime.model.SerialProbeResp;
import top.continew.admin.monitor.runtime.session.PortSession;
import top.continew.starter.core.util.validation.CheckUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 串口自动探测（FR-ACQ-06 波特率、FR-ACQ-07 从站地址）。
 *
 * <p>做法：按候选波特率逐个打开串口，用 Modbus RTU「读 1 个保持寄存器」试通讯；
 * 命中波特率后再扫描 1~N 的从站地址找在线设备。要求该端口当前没有被采集占用
 * （正在采集时请先停用端口或暂停采集）。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SerialProbeService {

    /** 常用波特率档位 */
    public static final List<Integer> DEFAULT_BAUD_RATES = List.of(1200, 2400, 4800, 9600, 19200,
        38400, 57600, 115200);

    /** 单个波特率的试探超时（毫秒） */
    private static final int PROBE_TIMEOUT_MS = 800;

    private final BizPortMapper portMapper;
    private final AcquisitionEngine acquisitionEngine;

    /**
     * 探测端口波特率与在线从站地址
     *
     * @param portId      端口 ID
     * @param address     已知从站地址（可空，默认 1）
     * @param baudRates   候选波特率（可空，默认常用档）
     * @param maxAddress  地址扫描上限（可空，默认 32）
     * @return 探测结果
     */
    public SerialProbeResp probe(Long portId, Integer address, List<Integer> baudRates,
        Integer maxAddress) {
        long start = System.currentTimeMillis();
        BizPortDO port = portMapper.selectById(portId);
        CheckUtils.throwIf(port == null, "端口不存在：{}", portId);
        CheckUtils.throwIf(!Protocols.PORT_TYPE_SERIAL.equalsIgnoreCase(port.getPortType()),
            "仅串口端口支持自动探测，当前端口类型为 {}", port.getPortType());
        int knownAddress = address == null ? 1 : address;
        List<Integer> candidates = baudRates == null || baudRates.isEmpty() ? DEFAULT_BAUD_RATES
            : baudRates;
        int limit = maxAddress == null ? 32 : maxAddress;
        SerialProbeResp resp = new SerialProbeResp();
        resp.setPortName(port.getPortName());
        // 端口正在采集中时不能自己再打开一次串口（会「拒绝访问」并被误判成「没有设备」）：
        // 复用采集会话探测。此时波特率已由端口配置确定，跳过波特率扫描直接扫地址。
        PortSession live = acquisitionEngine.sessionOfPort(portId);
        resp.setReused(live != null);
        Integer hit = null;
        if (live != null) {
            int configured = port.getBaudRate() == null ? 9600 : port.getBaudRate();
            if (this.canRead(live, knownAddress)) {
                hit = configured;
            }
            resp.setBaudAttempts(1);
        } else {
            for (Integer baud : candidates) {
                if (!canRead(port, baud, knownAddress)) {
                    continue;
                }
                hit = baud;
                break;
            }
            resp.setBaudAttempts(candidates.size());
        }
        resp.setBaudRate(hit);
        if (hit == null) {
            resp.setNote(live == null
                ? "未能在候选波特率上建立通讯，请确认设备已上电、从站地址与线路正常"
                : "端口正在采集但复用会话读不到应答，请检查设备与线路（波特率按端口配置 "
                    + port.getBaudRate() + "）");
            resp.setDurationMs(System.currentTimeMillis() - start);
            log.warn("串口 {} 波特率探测失败（复用会话={}）", port.getPortName(), live != null);
            return resp;
        }
        // 命中波特率后扫描从站地址
        for (int current = 1; current <= limit; current++) {
            boolean alive = live != null ? this.canRead(live, current) : this.canRead(port, hit,
                current);
            if (alive) {
                resp.getAddresses().add(current);
            }
        }
        resp.setAddressAttempts(limit);
        resp.setDurationMs(System.currentTimeMillis() - start);
        resp.setNote((live == null ? "" : "端口正在采集，已复用现有会话（未做波特率扫描）：")
            + "波特率 " + hit + "，扫描 1~" + limit + " 共发现 " + resp.getAddresses().size() + " 个从站");
        log.info("串口 {} 探测完成：{}", port.getPortName(), resp.getNote());
        return resp;
    }

    /**
     * 复用采集会话试读一个寄存器
     *
     * @param session 端口会话
     * @param address 从站地址
     * @return 有应答返回 true
     */
    private boolean canRead(PortSession session, int address) {
        try {
            session.readHoldingRegisters(address, 0, 1, PROBE_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            log.debug("复用会话探测失败（地址 {}）：{}", address, e.getMessage());
            return false;
        }
    }

    private boolean canRead(BizPortDO port, int baudRate, int address) {
        DeviceProtocol protocol = null;
        try {
            protocol = Protocols.create(Protocols.MODBUS_RTU, Protocols.PORT_TYPE_SERIAL, port
                .getPortName(), baudRate, port.getDataBits() == null ? 8 : port.getDataBits(),
                port
                    .getStopBits() == null ? 1 : port.getStopBits(),
                port.getParity() == null ? 0 : port
                    .getParity(),
                port.getFlowControl() == null ? 0 : port.getFlowControl(),
                PROBE_TIMEOUT_MS, 0);
            protocol.connect();
            protocol.readHoldingRegisters(address, 0, 1);
            return true;
        } catch (Exception e) {
            log.debug("探测失败（波特率 {}，地址 {}）：{}", baudRate, address, e.getMessage());
            return false;
        } finally {
            if (protocol != null) {
                try {
                    protocol.close();
                } catch (Exception e) {
                    log.debug("关闭探测连接失败：{}", e.getMessage());
                }
            }
        }
    }

    /**
     * 候选波特率（供前端下拉展示）
     *
     * @return 档位
     */
    public List<Integer> baudRates() {
        return new ArrayList<>(DEFAULT_BAUD_RATES);
    }
}
