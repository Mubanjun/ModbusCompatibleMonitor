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

package top.continew.admin.monitor.runtime.io;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizDataMapper;
import top.continew.admin.monitor.model.entity.BizDataDO;
import top.continew.admin.monitor.runtime.security.MaskUtils;
import top.continew.starter.core.util.validation.CheckUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 流式数据导出（FR-IO-02：按条件导出，大数据量采用流式导出避免内存溢出）。
 *
 * <p>按固定页大小循环取数并立即写出，内存占用与总行数无关；
 * 每页写出后 flush，浏览器可边下边收。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    /** 分页大小（内存上限 ≈ 页大小 × 单行对象） */
    private static final int PAGE_SIZE = 5000;

    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String CSV_HEADER =
        "deviceName,deviceAddr,nodeId,analogNo,elementName,unit,"
            + "dataValue,rawValue,alarmFlag,recordTime";

    private final BizDataMapper dataMapper;

    /**
     * 解析导出编码（NFR-COMPAT-07：UTF-8 与 GB18030 两种环境）
     *
     * <p>为空按 UTF-8；GBK 视作 GB18030（兼容写法）；其它值直接拒绝，避免导出乱码。</p>
     *
     * @param encoding 编码名
     * @return 字符集
     */
    public static Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        String normalized = encoding.trim().toUpperCase(Locale.ROOT).replace("-", "");
        if ("UTF8".equals(normalized)) {
            return StandardCharsets.UTF_8;
        }
        CheckUtils.throwIf(!"GB18030".equals(normalized) && !"GBK".equals(normalized),
            "仅支持 UTF-8 与 GB18030 两种导出编码：{}", encoding);
        return Charset.forName("GB18030");
    }

    /**
     * 流式导出历史数据为 CSV
     *
     * @param deviceId   设备 ID（可空）
     * @param nodeId     节点号（可空）
     * @param analogNo   模拟量序号（可空）
     * @param startTime  起始时间（可空）
     * @param endTime    结束时间（可空）
     * @param encoding   导出编码（可空= UTF-8；支持 GB18030）
     * @param out        输出流
     * @return 导出行数
     * @throws IOException 写出失败
     */
    public long exportDataCsv(Long deviceId, Integer nodeId, Integer analogNo,
        LocalDateTime startTime,
        LocalDateTime endTime, String encoding, OutputStream out) throws IOException {
        LambdaQueryWrapper<BizDataDO> wrapper = Wrappers.<BizDataDO>lambdaQuery()
            .eq(deviceId != null,
                BizDataDO::getDeviceId, deviceId)
            .eq(nodeId != null, BizDataDO::getNodeId, nodeId).eq(
                analogNo != null, BizDataDO::getAnalogNo, analogNo)
            .ge(startTime != null, BizDataDO::getRecordTime,
                startTime)
            .le(endTime != null, BizDataDO::getRecordTime, endTime)
            .orderByAsc(BizDataDO::getRecordTime,
                BizDataDO::getId);
        long rows = 0L;
        Charset charset = resolveCharset(encoding);
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(out, charset), 64 * 1024)) {
            // BOM 让 Excel 正确识别 UTF-8；GB18030 不能带 BOM（否则首列会出现乱码字符）
            if (StandardCharsets.UTF_8.equals(charset)) {
                writer.write('\ufeff');
            }
            writer.write(CSV_HEADER);
            writer.write('\n');
            int page = 1;
            while (true) {
                Page<BizDataDO> result =
                    dataMapper.selectPage(new Page<>(page, PAGE_SIZE), wrapper);
                List<BizDataDO> records = result.getRecords();
                for (BizDataDO record : records) {
                    writer.write(this.line(record));
                    writer.write('\n');
                }
                rows += records.size();
                writer.flush();
                if (records.size() < PAGE_SIZE) {
                    break;
                }
                page++;
            }
        }
        log.info("历史数据导出完成：rows={} deviceId={} nodeId={} analogNo={}", rows, deviceId, nodeId,
            analogNo);
        return rows;
    }

    private String line(BizDataDO record) {
        StringBuilder builder = new StringBuilder(128);
        // 自由文本列（设备名/要素名等由用户填写）统一过一遍脱敏（NFR-SEC-07）：
        // 万一被误填成手机号/邮箱/身份证，导出文件里也不会原样带出去
        builder.append(csv(MaskUtils.maskText(record.getDeviceName()))).append(',');
        builder.append(record.getDeviceAddr() == null ? "" : record.getDeviceAddr()).append(',');
        builder.append(record.getNodeId() == null ? "" : record.getNodeId()).append(',');
        builder.append(record.getAnalogNo() == null ? "" : record.getAnalogNo()).append(',');
        builder.append(csv(MaskUtils.maskText(record.getElementName()))).append(',');
        builder.append(csv(record.getUnit())).append(',');
        builder.append(record.getDataValue() == null ? "" : record.getDataValue()).append(',');
        builder.append(record.getRawValue() == null ? "" : record.getRawValue()).append(',');
        builder.append(record.getAlarmFlag() == null ? "" : record.getAlarmFlag()).append(',');
        builder.append(record.getRecordTime() == null ? "" : record.getRecordTime().format(TIME));
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
