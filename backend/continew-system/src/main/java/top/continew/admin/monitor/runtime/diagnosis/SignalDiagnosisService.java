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

package top.continew.admin.monitor.runtime.diagnosis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.mapper.BizSignalLogMapper;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizSignalLogDO;
import top.continew.admin.monitor.runtime.model.SignalDiagnosisItem;
import top.continew.admin.monitor.runtime.model.SignalDiagnosisResp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集诊断（FR-SIG-04）：基于最近信令给出「现象 + 处理建议」。
 *
 * <p>规格要求能针对超时、CRC 错误、从站无响应、地址冲突给出与现象匹配的建议。
 * 实现思路：取最近 N 条信令，按错误信息归类到固定现象，再附上该现象的现场处置步骤
 * （而不是把原始报错直接抛给运维看）。分类逻辑是纯函数，单测覆盖各分支。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalDiagnosisService {

    /** 读取超时 */
    public static final String CODE_TIMEOUT = "TIMEOUT";
    /** 从站无响应（0 字节应答） */
    public static final String CODE_NO_RESPONSE = "NO_RESPONSE";
    /** CRC 校验失败 */
    public static final String CODE_CRC = "CRC";
    /** 应答与请求不匹配（功能码/寄存器数量/从站地址） */
    public static final String CODE_MISALIGNED = "MISALIGNED";
    /** 设备返回 Modbus 异常码 */
    public static final String CODE_SLAVE_EXCEPTION = "SLAVE_EXCEPTION";
    /** 通道打开失败（串口被占用/不存在） */
    public static final String CODE_CHANNEL = "CHANNEL";
    /** 同端口从站地址冲突（配置层） */
    public static final String CODE_ADDRESS_CONFLICT = "ADDRESS_CONFLICT";
    /** 其它失败 */
    public static final String CODE_OTHER = "OTHER";
    /** 未发现异常 */
    public static final String CODE_NONE = "NONE";

    /** 默认分析条数 */
    private static final int DEFAULT_LIMIT = 200;
    /** 分析条数上限 */
    private static final int MAX_LIMIT = 2000;

    private final BizSignalLogMapper signalLogMapper;
    private final BizDeviceMapper deviceMapper;

    /**
     * 诊断最近信令
     *
     * @param deviceId 设备 ID（可空）
     * @param portId   端口 ID（可空）
     * @param limit    分析最近多少条（可空，默认 200，上限 2000）
     * @return 诊断结果
     */
    public SignalDiagnosisResp diagnose(Long deviceId, Long portId, Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        List<BizSignalLogDO> rows = signalLogMapper.lambdaQuery()
            .eq(deviceId != null, BizSignalLogDO::getDeviceId, deviceId)
            .eq(portId != null, BizSignalLogDO::getPortId, portId)
            .orderByDesc(BizSignalLogDO::getId)
            .last("LIMIT " + size)
            .list();
        SignalDiagnosisResp resp = new SignalDiagnosisResp();
        resp.setDeviceId(deviceId);
        resp.setPortId(portId);
        resp.setSampled(rows.size());
        long failed = rows.stream().filter(row -> !Boolean.TRUE.equals(row.getSuccess())).count();
        resp.setFailed((int) failed);
        resp.setSuccessRate(rows.isEmpty() ? null
            : Math.round((rows.size() - failed) * 1000.0 / rows.size()) / 10.0);
        // 现象归类：保持出现顺序，便于前端稳定展示
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> samples = new HashMap<>();
        for (BizSignalLogDO row : rows) {
            String code = classify(row.getErrorMsg());
            if (code == null) {
                if (Boolean.TRUE.equals(row.getSuccess())) {
                    continue;
                }
                // 失败但没有错误信息也要计入，保证「各现象条数之和 = 失败条数」这一可核对的不变量
                code = CODE_OTHER;
            }
            counts.merge(code, 1, Integer::sum);
            samples.putIfAbsent(code, row.getErrorMsg());
        }
        List<SignalDiagnosisItem> items = new ArrayList<>(counts.size() + 1);
        counts.forEach((code, count) -> items.add(this.item(code, count, samples.get(code))));
        List<String> conflicts = this.addressConflicts(portId);
        if (!conflicts.isEmpty()) {
            SignalDiagnosisItem conflict =
                this.item(CODE_ADDRESS_CONFLICT, conflicts.size(), String.join("；", conflicts));
            items.add(0, conflict);
        }
        int classified = items.stream().mapToInt(item -> item.getCount() == null ? 0 : item
            .getCount()).sum();
        log.debug("信令诊断完成：采样 {} 条，失败 {} 条，归类 {} 条", rows.size(), failed, classified);
        if (items.isEmpty()) {
            SignalDiagnosisItem none = new SignalDiagnosisItem();
            none.setCode(CODE_NONE);
            none.setTitle("未发现异常");
            none.setCount(0);
            none.setSuggestion("最近 " + rows.size() + " 条信令未出现可识别的通讯故障；如现场仍有丢数，"
                + "请扩大分析条数或检查设备侧上报周期。");
            items.add(none);
        }
        resp.setItems(items);
        return resp;
    }

    /**
     * 按错误信息归类现象（纯函数，便于单测）
     *
     * @param errorMsg 信令错误信息
     * @return 现象代码，正常信令返回 null
     */
    public static String classify(String errorMsg) {
        if (errorMsg == null || errorMsg.isBlank()) {
            return null;
        }
        // 由具体到宽泛：错位与 CRC 都可能在消息里带「超时」等字样，必须先判
        if (errorMsg.contains("CRC")) {
            return CODE_CRC;
        }
        if (errorMsg.contains("寄存器数量与请求不符") || errorMsg.contains("功能码不匹配")
            || errorMsg.contains("从站地址不匹配")) {
            return CODE_MISALIGNED;
        }
        if (errorMsg.contains("异常响应")) {
            return CODE_SLAVE_EXCEPTION;
        }
        if (errorMsg.contains("实际仅收到 0 字节") || errorMsg.contains("从站无应答")) {
            return CODE_NO_RESPONSE;
        }
        if (errorMsg.contains("超时")) {
            return CODE_TIMEOUT;
        }
        if (errorMsg.contains("打开通道失败") || errorMsg.contains("拒绝访问")
            || errorMsg.contains("串口")) {
            return CODE_CHANNEL;
        }
        return CODE_OTHER;
    }

    /**
     * 现象对应的处理建议（纯函数，便于单测与前端展示）
     *
     * @param code 现象代码
     * @return 建议文本
     */
    public static String suggestion(String code) {
        return switch (code) {
            case CODE_TIMEOUT -> "读取超时：① 确认设备已上电、A/B 线未接反；② 核对端口波特率/数据位/停止位/校验位"
                + "与设备一致；③ 用「串口自动探测」确认从站地址；④ 若经串口服务器/桥接器，单帧超时与帧间静默适当加大。";
            case CODE_NO_RESPONSE -> "从站无响应（0 字节应答）：优先怀疑地址或波特率不匹配、线路断开；"
                + "先用自动探测扫地址，再核对通道寄存器地址是否超出设备范围。";
            case CODE_CRC -> "CRC 校验失败：多为波特率/校验位不一致、A/B 接反、缺终端电阻或强干扰；"
                + "检查屏蔽层与接地，总线两端加 120Ω 终端电阻，远离变频器等干扰源。";
            case CODE_MISALIGNED -> "应答与请求不匹配（功能码/寄存器数量/从站地址）：总线上可能有其它主机在轮询，"
                + "或上一帧应答迟到（大延迟桥接设备）；确认只有本平台在采集，必要时提高帧间静默。";
            case CODE_SLAVE_EXCEPTION -> "设备返回 Modbus 异常码：按异常码处理——非法数据地址核对通道 regAddr，"
                + "非法数据值核对写入范围，从站设备忙则降低轮询频率或缩短单帧超时。";
            case CODE_CHANNEL -> "通道打开失败：串口被其它程序占用、不存在或权限不足；"
                + "确认没有第二个实例/调试工具占用该串口，必要时重启服务释放句柄。";
            case CODE_ADDRESS_CONFLICT -> "从站地址冲突：同一端口下存在重复的从站地址，必须改正（新配置已被拦截，"
                + "历史数据需人工修正），否则两台设备会互相应答。";
            default -> "存在其它通讯失败：请结合信令明细中的收发报文与解析结果进一步定位。";
        };
    }

    private SignalDiagnosisItem item(String code, int count, String sample) {
        SignalDiagnosisItem item = new SignalDiagnosisItem();
        item.setCode(code);
        item.setCount(count);
        item.setTitle(title(code));
        item.setSuggestion(suggestion(code));
        item.setSample(sample);
        return item;
    }

    private static String title(String code) {
        return switch (code) {
            case CODE_TIMEOUT -> "读取超时";
            case CODE_NO_RESPONSE -> "从站无响应";
            case CODE_CRC -> "CRC 校验失败";
            case CODE_MISALIGNED -> "应答与请求不匹配";
            case CODE_SLAVE_EXCEPTION -> "设备返回异常码";
            case CODE_CHANNEL -> "通道打开失败";
            case CODE_ADDRESS_CONFLICT -> "从站地址冲突";
            default -> "其它通讯失败";
        };
    }

    /**
     * 找出同一端口下重复的从站地址（配置层冲突）
     *
     * @param portId 端口 ID（为空则检查全部端口）
     * @return 冲突描述
     */
    private List<String> addressConflicts(Long portId) {
        List<BizDeviceDO> devices = deviceMapper.lambdaQuery()
            .eq(portId != null, BizDeviceDO::getPortId, portId)
            .list();
        Map<String, Integer> counter = new HashMap<>();
        for (BizDeviceDO device : devices) {
            if (device.getSlaveAddr() == null) {
                continue;
            }
            String key = device.getPortId() + "#" + device.getSlaveAddr();
            counter.merge(key, 1, Integer::sum);
        }
        List<String> conflicts = new ArrayList<>();
        counter.forEach((key, count) -> {
            if (count > 1) {
                conflicts.add("端口 " + key.replace("#", " 从站地址 ") + " 出现 " + count + " 次");
            }
        });
        return conflicts;
    }
}
