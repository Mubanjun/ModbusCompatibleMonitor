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

package top.continew.admin.monitor.runtime.signal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizSignalLogMapper;
import top.continew.admin.monitor.model.entity.BizSignalLogDO;
import top.continew.admin.monitor.runtime.security.MaskUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 信令记录导出（FR-SIG-03）。
 *
 * <p>键集分页 + 流式写出：内存占用与记录条数无关；导出的是落库后的信令（biz_signal_log），
 * 不含仅在内存环形缓冲里的最近若干条。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalExportService {

    /** 单页条数 */
    private static final int PAGE_SIZE = 5000;

    /** 导出上限（防止把整库信令一次拉走） */
    private static final long MAX_ROWS = 2_000_000L;

    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final String HEADER =
        "id,traceTime,portName,deviceId,deviceAddr,direction,frame,"
            + "frameLength,parsed,durationMs,success,errorMsg";

    private final BizSignalLogMapper signalLogMapper;

    /**
     * 导出信令为 CSV
     *
     * @param deviceId  设备 ID（可空）
     * @param portId    端口 ID（可空）
     * @param success   是否成功（可空）
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @param out       输出流
     * @return 导出行数
     * @throws IOException 写出失败
     */
    public long exportCsv(Long deviceId, Long portId, Boolean success, LocalDateTime startTime,
        LocalDateTime endTime, OutputStream out) throws IOException {
        long rows = 0L;
        Long lastId = null;
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8),
            64 * 1024)) {
            writer.write('\ufeff');
            writer.write(HEADER);
            writer.write('\n');
            while (rows < MAX_ROWS) {
                LambdaQueryWrapper<BizSignalLogDO> wrapper = Wrappers.<BizSignalLogDO>lambdaQuery()
                    .gt(
                        lastId != null, BizSignalLogDO::getId, lastId)
                    .eq(deviceId != null,
                        BizSignalLogDO::getDeviceId, deviceId)
                    .eq(portId != null, BizSignalLogDO::getPortId, portId)
                    .eq(success != null, BizSignalLogDO::getSuccess, success)
                    .ge(startTime != null, BizSignalLogDO::getTraceTime, startTime)
                    .le(endTime != null,
                        BizSignalLogDO::getTraceTime, endTime)
                    .orderByAsc(BizSignalLogDO::getId)
                    .last("LIMIT " + PAGE_SIZE);
                List<BizSignalLogDO> page = signalLogMapper.selectList(wrapper);
                if (page.isEmpty()) {
                    break;
                }
                for (BizSignalLogDO row : page) {
                    writer.write(this.line(row));
                    writer.write('\n');
                }
                rows += page.size();
                lastId = page.get(page.size() - 1).getId();
                writer.flush();
                if (page.size() < PAGE_SIZE) {
                    break;
                }
            }
        }
        log.info("信令导出完成：rows={} deviceId={} portId={}", rows, deviceId, portId);
        return rows;
    }

    private String line(BizSignalLogDO row) {
        StringBuilder builder = new StringBuilder(160);
        builder.append(row.getId() == null ? "" : row.getId()).append(',');
        builder.append(row.getTraceTime() == null ? "" : row.getTraceTime().format(TIME))
            .append(',');
        // 自由文本列统一脱敏（NFR-SEC-07）：端口名/解析结果等可能被误填敏感信息
        builder.append(csv(MaskUtils.maskText(row.getPortName()))).append(',');
        builder.append(row.getDeviceId() == null ? "" : row.getDeviceId()).append(',');
        builder.append(row.getDeviceAddr() == null ? "" : row.getDeviceAddr()).append(',');
        builder.append(csv(row.getDirection())).append(',');
        builder.append(csv(row.getFrame())).append(',');
        builder.append(row.getFrameLength() == null ? "" : row.getFrameLength()).append(',');
        builder.append(csv(MaskUtils.maskText(row.getParsed()))).append(',');
        builder.append(row.getDurationMs() == null ? "" : row.getDurationMs()).append(',');
        builder.append(Boolean.TRUE.equals(row.getSuccess()) ? "1" : "0").append(',');
        builder.append(csv(row.getErrorMsg()));
        return builder.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
