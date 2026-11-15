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

package top.continew.admin.monitor.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.monitor.model.req.SerialProbeReq;
import top.continew.admin.monitor.model.req.TransactReq;
import top.continew.admin.monitor.runtime.alarm.AlarmEngine;
import top.continew.admin.monitor.runtime.alarm.AlarmNotifyDispatcher;
import top.continew.admin.monitor.runtime.buffer.StagingBufferService;
import top.continew.admin.monitor.runtime.engine.AcquisitionEngine;
import top.continew.admin.monitor.runtime.engine.SerialProbeService;
import top.continew.admin.monitor.runtime.report.ReportEngine;
import top.continew.admin.monitor.runtime.model.AcquisitionStatusResp;
import top.continew.admin.monitor.runtime.model.DeviceTestResp;
import top.continew.admin.monitor.runtime.diagnosis.SignalDiagnosisService;
import top.continew.admin.monitor.runtime.model.SerialProbeResp;
import top.continew.admin.monitor.runtime.model.SignalDiagnosisResp;
import top.continew.admin.monitor.runtime.model.SignalFrameResp;
import top.continew.admin.monitor.runtime.model.StagingStatusResp;
import top.continew.admin.monitor.runtime.model.TransactResp;
import top.continew.admin.monitor.runtime.signal.SignalExportService;
import top.continew.admin.monitor.runtime.signal.SignalRecorder;
import top.continew.starter.log.annotation.Log;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集运行时 API（FR-ACQ-08/10、FR-DEV-13、FR-SIG-01/02）
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Tag(name = "采集运行时 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/acquisition")
public class AcquisitionController {

    private final AcquisitionEngine acquisitionEngine;
    private final SignalRecorder signalRecorder;
    private final StagingBufferService stagingBufferService;
    private final AlarmEngine alarmEngine;
    private final AlarmNotifyDispatcher alarmNotifyDispatcher;
    private final ReportEngine reportEngine;
    private final SerialProbeService serialProbeService;
    private final SignalExportService signalExportService;
    private final SignalDiagnosisService signalDiagnosisService;

    /**
     * 查询采集运行状态
     *
     * @return 运行状态
     */
    @Operation(summary = "查询采集运行状态", description = "查询采集内核的启停状态、指标与端口设备状态")
    @SaCheckPermission("monitor:acquisition:status")
    @GetMapping("/status")
    public AcquisitionStatusResp status() {
        return acquisitionEngine.snapshot();
    }

    /**
     * 开始采集
     */
    @Operation(summary = "开始采集")
    @SaCheckPermission("monitor:acquisition:control")
    @Log
    @PostMapping("/start")
    public void start() {
        acquisitionEngine.start();
    }

    /**
     * 停止采集
     */
    @Operation(summary = "停止采集")
    @SaCheckPermission("monitor:acquisition:control")
    @Log
    @PostMapping("/stop")
    public void stop() {
        acquisitionEngine.stop();
    }

    /**
     * 重新加载采集配置
     */
    @Operation(summary = "重新加载采集配置", description = "重新读取端口、设备、通道与采集顺序配置并重建连接")
    @SaCheckPermission("monitor:acquisition:control")
    @Log
    @PostMapping("/reload")
    public void reload() {
        acquisitionEngine.reload();
    }

    /**
     * 暂停采集
     *
     * @param portId 端口 ID，为空表示全部暂停
     */
    @Operation(summary = "暂停采集", description = "暂停全部采集或指定端口（FR-ACQ-08）")
    @Parameter(name = "portId", description = "端口 ID，为空表示全部暂停", in = ParameterIn.QUERY)
    @SaCheckPermission("monitor:acquisition:control")
    @Log
    @PostMapping("/pause")
    public void pause(@RequestParam(required = false) Long portId) {
        acquisitionEngine.pause(portId);
    }

    /**
     * 恢复采集
     *
     * @param portId 端口 ID，为空表示全部恢复
     */
    @Operation(summary = "恢复采集", description = "恢复全部采集或指定端口")
    @Parameter(name = "portId", description = "端口 ID，为空表示全部恢复", in = ParameterIn.QUERY)
    @SaCheckPermission("monitor:acquisition:control")
    @Log
    @PostMapping("/resume")
    public void resume(@RequestParam(required = false) Long portId) {
        acquisitionEngine.resume(portId);
    }

    /**
     * 单设备通讯测试
     *
     * @param deviceId 设备 ID
     * @return 测试结果
     */
    @Operation(summary = "单设备通讯测试", description = "对单设备执行一次连通测试，返回报文、原始值与工程量（FR-DEV-13）")
    @Parameter(name = "deviceId", description = "设备 ID", in = ParameterIn.PATH)
    @SaCheckPermission("monitor:acquisition:test")
    @PostMapping("/device/{deviceId}/test")
    public DeviceTestResp testDevice(@PathVariable Long deviceId) {
        return acquisitionEngine.testDevice(deviceId);
    }

    /**
     * 导出信令记录
     *
     * @param deviceId  设备 ID
     * @param portId    端口 ID
     * @param success   是否成功
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @param response  响应
     * @throws IOException 写出失败
     */
    @Operation(summary = "导出信令记录", description = "按条件流式导出信令为 CSV（FR-SIG-03）")
    @SaCheckPermission("monitor:signalLog:list")
    @GetMapping("/signal/export")
    public void exportSignals(@RequestParam(required = false) Long deviceId,
        @RequestParam(required = false) Long portId,
        @RequestParam(required = false) Boolean success,
        @RequestParam(required = false) @DateTimeFormat(
            pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @RequestParam(required = false) @DateTimeFormat(
            pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
        HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=signal-log.csv");
        signalExportService.exportCsv(deviceId, portId, success, startTime, endTime, response
            .getOutputStream());
        response.flushBuffer();
    }

    /**
     * 信令诊断建议（FR-SIG-04）
     *
     * @param deviceId 设备 ID（可空）
     * @param portId   端口 ID（可空）
     * @param limit    分析最近多少条（可空，默认 200）
     * @return 诊断结果
     */
    @Operation(summary = "信令诊断建议", description = "基于最近信令给出诊断建议：超时/无响应/CRC/帧错位/异常码/地址冲突（FR-SIG-04）")
    @SaCheckPermission("monitor:signalLog:list")
    @GetMapping("/signal/diagnose")
    public SignalDiagnosisResp diagnoseSignal(@RequestParam(required = false) Long deviceId,
        @RequestParam(required = false) Long portId,
        @RequestParam(required = false) Integer limit) {
        return signalDiagnosisService.diagnose(deviceId, portId, limit);
    }

    /**
     * 自定义帧透传
     *
     * @param deviceId 设备 ID
     * @param req      请求
     * @return 透传结果
     */
    @Operation(summary = "自定义帧透传", description = "向指定设备发送原始帧并可选读取应答（FR-PROTO-08）")
    @Parameter(name = "deviceId", description = "设备 ID", in = ParameterIn.PATH)
    @SaCheckPermission("monitor:acquisition:test")
    @Log
    @PostMapping("/device/{deviceId}/transact")
    public TransactResp transact(@PathVariable Long deviceId, @RequestBody @Valid TransactReq req) {
        return acquisitionEngine.transact(deviceId, req.getFrame(), req.getExpectedLength());
    }

    /**
     * 串口自动探测（波特率与从站地址）
     *
     * @param req 请求
     * @return 探测结果
     */
    @Operation(summary = "串口自动探测", description = "按候选波特率与从站地址扫描串口在线设备（FR-ACQ-06/07）")
    @SaCheckPermission("monitor:acquisition:test")
    @Log
    @PostMapping("/probe")
    public SerialProbeResp probe(@RequestBody @Valid SerialProbeReq req) {
        return serialProbeService.probe(req.getPortId(), req.getAddress(), req.getBaudRates(), req
            .getMaxAddress());
    }

    /**
     * 增量拉取实时信令
     *
     * @param since 起始序号（不含），为空表示从当前时刻开始
     * @param limit 最大返回条数
     * @return 信令列表
     */
    @Operation(summary = "增量拉取实时信令", description = "按序号增量拉取实时信令流（FR-SIG-01）")
    @Parameter(name = "since", description = "起始序号（不含）", in = ParameterIn.QUERY)
    @Parameter(name = "limit", description = "最大返回条数", in = ParameterIn.QUERY)
    @SaCheckPermission("monitor:signalLog:list")
    @GetMapping("/signal/recent")
    public List<SignalFrameResp> recentSignals(@RequestParam(required = false) Long since,
        @RequestParam(defaultValue = "200") @Min(value = 1,
            message = "单次最多拉取 1~2000 条") @Max(value = 2000,
                message = "单次最多拉取 1~2000 条") Integer limit) {
        long from = since == null ? signalRecorder.currentSequence() : since;
        return signalRecorder.recent(from, limit).stream().map(SignalFrameResp::of).toList();
    }

    /**
     * 查询本地暂存与落地状态
     *
     * @return 暂存状态
     */
    @Operation(summary = "查询本地暂存与落地状态", description = "展示积压条数、最旧数据时间、写入与上传速率（FR-BUF-06）")
    @SaCheckPermission("monitor:acquisition:status")
    @GetMapping("/buffer")
    public StagingStatusResp bufferStatus() {
        return stagingBufferService.status();
    }

    /**
     * 查询报警引擎与通知状态
     *
     * @return 报警运行状态
     */
    @Operation(summary = "查询报警引擎状态", description = "报警跟踪点数、活动报警、累计报警与通知分发统计（FR-ALM）")
    @SaCheckPermission("monitor:acquisition:status")
    @GetMapping("/alarm")
    public Map<String, Object> alarmStatus() {
        Map<String, Object> result = new LinkedHashMap<>(alarmEngine.stats());
        result.put("notify", alarmNotifyDispatcher.stats());
        return result;
    }

    /**
     * 查询上报引擎状态
     *
     * @return 上报运行状态
     */
    @Operation(summary = "查询上报引擎状态", description = "各目标的发送批次、条数、失败次数与最近错误（FR-RPT-08）")
    @SaCheckPermission("monitor:acquisition:status")
    @GetMapping("/report")
    public Map<String, Object> reportStatus() {
        return reportEngine.status();
    }

    /**
     * 立即执行一次暂存落地
     */
    @Operation(summary = "立即执行一次暂存落地", description = "手动触发暂存批次上传，用于排障")
    @SaCheckPermission("monitor:acquisition:control")
    @Log
    @PostMapping("/buffer/flush")
    public void flushBuffer() {
        stagingBufferService.cycle();
    }

    /**
     * 查询信令游标
     *
     * @return 当前最大信令序号
     */
    @Operation(summary = "查询信令游标", description = "返回当前最大信令序号，供前端建立增量拉取起点")
    @SaCheckPermission("monitor:signalLog:list")
    @GetMapping("/signal/cursor")
    public Long signalCursor() {
        return signalRecorder.currentSequence();
    }
}
