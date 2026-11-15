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

package top.continew.admin.monitor.runtime.service;

import com.mbj.jdcompatiblemonitor.device.AnalogDefinition;
import com.mbj.jdcompatiblemonitor.device.ChannelConfig;
import com.mbj.jdcompatiblemonitor.device.ChannelDataType;
import com.mbj.jdcompatiblemonitor.device.ElementType;
import com.mbj.jdcompatiblemonitor.device.runtime.AcquisitionSettings;
import com.mbj.jdcompatiblemonitor.device.runtime.CircuitBreaker;
import com.mbj.jdcompatiblemonitor.protocol.DeviceProtocol;
import com.mbj.jdcompatiblemonitor.protocol.Protocols;
import com.mbj.jdcompatiblemonitor.protocol.ValueEncoding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizChannelMapper;
import top.continew.admin.monitor.mapper.BizCollectOrderMapper;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizPortMapper;
import top.continew.admin.monitor.model.entity.BizChannelDO;
import top.continew.admin.monitor.model.entity.BizCollectOrderDO;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizPortDO;
import top.continew.admin.monitor.runtime.session.ChannelBinding;
import top.continew.admin.monitor.runtime.session.DeviceSession;
import top.continew.admin.monitor.runtime.session.PortSession;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 把配置库中的端口 / 设备 / 通道 / 采集顺序装配成可运行的会话拓扑。
 *
 * <p>协议实例由 {@link Protocols} 工厂统一创建（FR-PROTO-05：新增协议不改调度），
 * 装配过程对单个坏配置容错，不因一台设备写错而整体起不来。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcquisitionConfigLoader {

    /** 从站地址下限（与 biz_device.slave_addr 一致） */
    private static final int MIN_CHANNEL_NO = 1;
    /** 从站地址上限 */
    private static final int MAX_CHANNEL_NO = 255;

    private final BizPortMapper bizPortMapper;
    private final BizDeviceMapper bizDeviceMapper;
    private final BizChannelMapper bizChannelMapper;
    private final BizCollectOrderMapper bizCollectOrderMapper;
    private final RuntimeSettingsService runtimeSettingsService;

    /**
     * 加载当前配置，生成端口会话列表
     *
     * @return 采集拓扑
     */
    public AcquisitionTopology load() {
        AcquisitionSettings settings = runtimeSettingsService.reload();
        // 缺陷 71：端口/通道的「是否启用」此前在采集链路上无人读取——禁用后仍被轮询、仍写库，
        // 页面显示「禁用」而库里一直有新数据；被禁用的端口还会继续占用串口。
        // 两列都是 NOT NULL DEFAULT b'1'，按 true 过滤不会误伤历史数据（设备/采集顺序的启用判定原本就有）。
        List<BizPortDO> ports = bizPortMapper.lambdaQuery().eq(BizPortDO::getEnabled, true).list();
        List<BizDeviceDO> devices = bizDeviceMapper.lambdaQuery().list();
        List<BizChannelDO> channels =
            bizChannelMapper.lambdaQuery().eq(BizChannelDO::getEnabled, true)
                .list();
        List<BizCollectOrderDO> orders = bizCollectOrderMapper.lambdaQuery().list();

        Map<Long, Map<Integer, List<BizChannelDO>>> channelGroups = groupChannels(channels);
        Map<Long, BizCollectOrderDO> orderIndex = new HashMap<>();
        for (BizCollectOrderDO order : orders) {
            if (order.getDeviceId() != null) {
                orderIndex.put(order.getDeviceId(), order);
            }
        }

        Map<Long, List<DeviceSession>> devicesByPort = new HashMap<>();
        for (BizDeviceDO device : devices) {
            if (device.getPortId() == null) {
                continue;
            }
            List<ChannelBinding> bindings = buildBindings(channelGroups.get(device.getId()));
            BizCollectOrderDO order = orderIndex.get(device.getId());
            int sortNo = order != null && order.getSortNo() != null
                ? order.getSortNo()
                : (device.getSortNo() == null ? 999 : device.getSortNo());
            boolean enabled = order == null || Boolean.TRUE.equals(order.getEnabled());
            CircuitBreaker breaker = new CircuitBreaker(settings.failureThreshold(),
                settings.circuitOpenMs(), settings.maxBackoffMs());
            devicesByPort.computeIfAbsent(device.getPortId(), key -> new ArrayList<>())
                .add(new DeviceSession(device, sortNo, enabled, bindings, breaker));
        }

        List<PortSession> sessions = new ArrayList<>();
        for (BizPortDO port : ports) {
            List<DeviceSession> portDevices = devicesByPort.getOrDefault(port.getId(),
                new ArrayList<>());
            portDevices.sort(Comparator.naturalOrder());
            PortSession session = this.createSession(port, portDevices, settings);
            if (session == null) {
                continue;
            }
            session.setDevices(portDevices);
            sessions.add(session);
        }
        log.info("采集拓扑加载完成：端口 {} 个，设备 {} 台", sessions.size(), devices.size());
        return new AcquisitionTopology(sessions);
    }

    /**
     * 创建端口会话
     *
     * @param port     端口配置
     * @param devices  端口下设备
     * @param settings 采集参数
     * @return 端口会话，创建失败返回 null
     */
    private PortSession createSession(BizPortDO port, List<DeviceSession> devices,
        AcquisitionSettings settings) {
        // 协议按「已启用的设备」决定：停用的设备不参与采集，也不应该决定总线编码。
        // 否则停掉旧协议设备后端口仍按旧协议组帧，现场表现为「换了协议就一直通讯失败」。
        List<DeviceSession> activeDevices = devices.stream()
            .filter(item -> item.isEnabled() && Boolean.TRUE.equals(item.getDevice().getEnabled()))
            .toList();
        List<DeviceSession> protocolSource = activeDevices.isEmpty() ? devices : activeDevices;
        String protocolName = protocolSource.stream()
            .map(item -> item.getDevice().getProtocol())
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(Protocols.RS_MODBUS);
        // 历史数据里可能存在同端口多协议（新配置已被 BizDeviceServiceImpl 拦住）：
        // 这里按第一台启用设备的协议建会话，但必须显式告警，避免无声地用错编码
        boolean mixedProtocol = protocolSource.stream()
            .map(item -> item.getDevice().getProtocol())
            .filter(Objects::nonNull)
            .anyMatch(name -> !name.equalsIgnoreCase(protocolName));
        if (mixedProtocol) {
            log.warn("端口 [{}] 下存在多种协议，已按第一台设备的协议 [{}] 建立会话；"
                + "同一条链路只能有一种编码，请把不同协议设备拆到不同端口", port.getName(), protocolName);
        }
        int timeout = settings.timeoutMs();
        if (!devices.isEmpty()) {
            timeout = settings.resolveTimeout(devices.get(0).getDevice().getTimeoutMs());
        }
        try {
            DeviceProtocol protocol = Protocols.create(protocolName, port.getPortType(),
                port.getPortName(), defaultInt(port.getBaudRate(), 4800),
                defaultInt(port.getDataBits(), 8), defaultInt(port.getStopBits(), 1),
                defaultInt(port.getParity(), 0), defaultInt(port.getFlowControl(), 0), timeout,
                settings.retries());
            return new PortSession(port, protocol);
        } catch (Exception e) {
            log.error("端口 [{}] 协议装配失败，已跳过：{}", port.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 按设备与通道号归集通道配置
     *
     * @param channels 通道配置行
     * @return 设备 ID -&gt; 通道号 -&gt; 配置行
     */
    private static Map<Long, Map<Integer, List<BizChannelDO>>> groupChannels(
        Collection<BizChannelDO> channels) {
        Map<Long, Map<Integer, List<BizChannelDO>>> groups = new HashMap<>();
        for (BizChannelDO channel : channels) {
            if (channel.getDeviceId() == null || channel.getChannelNo() == null) {
                continue;
            }
            groups.computeIfAbsent(channel.getDeviceId(), key -> new HashMap<>())
                .computeIfAbsent(channel.getChannelNo(), key -> new ArrayList<>())
                .add(channel);
        }
        return groups;
    }

    /**
     * 把一组通道配置行转换为采集绑定
     *
     * @param groups 设备下的通道分组
     * @return 采集绑定列表
     */
    private static List<ChannelBinding> buildBindings(Map<Integer, List<BizChannelDO>> groups) {
        List<ChannelBinding> bindings = new ArrayList<>();
        if (groups == null) {
            return bindings;
        }
        List<Integer> channelNos = new ArrayList<>(groups.keySet());
        channelNos.sort(Comparator.naturalOrder());
        for (Integer channelNo : channelNos) {
            if (channelNo == null || channelNo < MIN_CHANNEL_NO || channelNo > MAX_CHANNEL_NO) {
                log.warn("通道号 [{}] 超出 1~255，已跳过", channelNo);
                continue;
            }
            List<BizChannelDO> rows = groups.get(channelNo);
            ChannelDataType dataType = resolveDataType(rows);
            AnalogDefinition analog1 = AnalogDefinition.disabled();
            AnalogDefinition analog2 = AnalogDefinition.disabled();
            int regAddr = Integer.MAX_VALUE;
            for (BizChannelDO row : rows) {
                if (row.getRegAddr() != null) {
                    regAddr = Math.min(regAddr, row.getRegAddr());
                }
                if (Integer.valueOf(1).equals(row.getAnalogNo())) {
                    analog1 = toAnalog(row);
                } else if (Integer.valueOf(2).equals(row.getAnalogNo())) {
                    analog2 = toAnalog(row);
                }
            }
            warnIncompleteChannel(channelNo, dataType, analog1, analog2);
            bindings.add(new ChannelBinding(channelNo, regAddr == Integer.MAX_VALUE ? 0 : regAddr,
                dataType.requiredRegisterCount(),
                new ChannelConfig(channelNo, dataType, analog1, analog2)));
        }
        return bindings;
    }

    /**
     * 提示「数据属性要求的模拟量缺少通道行」的配置不一致
     *
     * <p>厂家配置软件里禁用的模拟量默认 A=0.1、名称与单位为空，其寄存器是无效值。
     * 若数据属性声明某一路启用却缺少对应通道行，该路会被丢弃（读取层同样按禁用处理），
     * 这里提前告警，避免运维拿着「读不到数」的现场排查协议。</p>
     *
     * @param channelNo 通道号
     * @param dataType  数据属性
     * @param analog1   模拟量1 定义
     * @param analog2   模拟量2 定义
     */
    private static void warnIncompleteChannel(int channelNo, ChannelDataType dataType,
        AnalogDefinition analog1, AnalogDefinition analog2) {
        boolean missing1 = !analog1.isConfigured();
        boolean missing2 = !analog2.isConfigured();
        switch (dataType) {
            case BOTH -> {
                if (missing1 || missing2) {
                    log.warn("通道 {} 数据属性为 BOTH（模拟量1/2 均启用），但缺少 {} 的通道配置："
                        + "该路将按禁用丢弃（主机侧寄存器为无效值），请补齐通道参数", channelNo,
                        missing1 ? "模拟量1" : "模拟量2");
                }
            }
            case FLOAT32 -> {
                if (missing1) {
                    log.warn("通道 {} 为浮点型数据，但缺少模拟量1 的通道配置（浮点值的名称/单位/系数取自"
                        + "模拟量1），设备将报通道配置错误，请补齐", channelNo);
                }
            }
            case ANALOG1_ONLY -> {
                if (missing1) {
                    log.warn("通道 {} 只启用模拟量1，但缺少模拟量1 的通道配置，请补齐", channelNo);
                }
            }
            case ANALOG2_ONLY -> {
                if (missing2) {
                    log.warn("通道 {} 只启用模拟量2，但缺少模拟量2 的通道配置，请补齐", channelNo);
                }
            }
            default -> {
                // 无需提示
            }
        }
    }

    /**
     * 解析通道数据属性：优先取模拟量1 所在行，其次取任意可解析行
     *
     * @param rows 通道配置行
     * @return 数据属性
     */
    private static ChannelDataType resolveDataType(List<BizChannelDO> rows) {
        ChannelDataType fallback = null;
        for (BizChannelDO row : rows) {
            ChannelDataType parsed = parseEnum(ChannelDataType.class, row.getDataType());
            if (parsed == null) {
                continue;
            }
            if (Integer.valueOf(1).equals(row.getAnalogNo())) {
                return parsed;
            }
            if (fallback == null) {
                fallback = parsed;
            }
        }
        return fallback == null ? ChannelDataType.ANALOG2_ONLY : fallback;
    }

    /**
     * 把一行通道配置转换为模拟量定义
     *
     * @param row 通道配置行
     * @return 模拟量定义
     */
    private static AnalogDefinition toAnalog(BizChannelDO row) {
        ElementType elementType = parseEnum(ElementType.class, row.getElementType());
        ValueEncoding encoding = parseEnum(ValueEncoding.class, row.getEncoding());
        String name = row.getElementName();
        if (name == null || name.isBlank()) {
            name = elementType == null ? null : elementType.displayName();
        }
        return new AnalogDefinition(elementType, name, row.getUnit(),
            encoding == null ? ValueEncoding.UINT16_SCALE_1 : encoding,
            doubleValue(row.getCoefficient(), 1.0D), doubleValue(row.getOffsetValue(), 0.0D),
            // 缺陷 70：量程未配置（NULL）必须表示「不判定超量程」。原先折成 0.0 会把未选量程档位的通道
            // 变成「下限 0、上限 0」，任何非零读数都被判超量程、quality=BAD、over_range=1 落库，
            // 而 AcquisitionSample 的契约恰恰是「无上下限配置时视为在量程内」。
            row.getMaxValue() == null ? Double.MAX_VALUE : row.getMaxValue().doubleValue(),
            row.getMinValue() == null ? -Double.MAX_VALUE : row.getMinValue().doubleValue());
    }

    /**
     * 安全地把字符串解析为枚举
     *
     * @param type  枚举类型
     * @param value 字符串
     * @param <E>   枚举泛型
     * @return 枚举值，无法解析返回 null
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException e) {
            log.warn("无法识别的 {} 取值：{}", type.getSimpleName(), value);
            return null;
        }
    }

    private static int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static double doubleValue(BigDecimal value, double fallback) {
        return value == null ? fallback : value.doubleValue();
    }
}
