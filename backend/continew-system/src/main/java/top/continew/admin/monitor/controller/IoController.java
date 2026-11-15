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
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StreamUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.monitor.model.req.BackupCreateReq;
import top.continew.admin.monitor.model.req.BackupPasswordReq;
import top.continew.admin.monitor.model.req.BackupRestoreReq;
import top.continew.admin.monitor.runtime.io.BackupProperties;
import top.continew.admin.monitor.runtime.io.BackupScheduler;
import top.continew.admin.monitor.runtime.io.BackupService;
import top.continew.admin.monitor.runtime.io.ExportService;
import top.continew.admin.monitor.runtime.io.ImportService;
import top.continew.admin.monitor.runtime.io.ImportType;
import top.continew.admin.monitor.runtime.io.SqlDumpService;
import top.continew.admin.monitor.runtime.model.BackupFileResp;
import top.continew.admin.monitor.runtime.model.BackupRestoreResp;
import top.continew.admin.monitor.runtime.model.BackupVerifyResp;
import top.continew.admin.monitor.runtime.model.ImportPreviewResp;
import top.continew.admin.monitor.runtime.model.ImportReportResp;
import top.continew.starter.log.annotation.Log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入导出与备份恢复 API（FR-IO-01~07）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Tag(name = "导入导出与备份恢复 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/io")
public class IoController {

    private final ImportService importService;
    private final ExportService exportService;
    private final SqlDumpService sqlDumpService;
    private final BackupService backupService;
    private final BackupScheduler backupScheduler;
    private final BackupProperties backupProperties;

    /**
     * 导入前校验（表头、行数、样例）
     *
     * @param type 导入类型
     * @param file 文件
     * @return 预览结果
     */
    @Operation(summary = "导入预览", description = "校验表头并返回前 20 行样例（FR-IO-01）")
    @Parameter(name = "type", description = "DEVICE / HISTORY / CONFIG", in = ParameterIn.QUERY)
    @SaCheckPermission("monitor:io:import")
    @PostMapping("/import/preview")
    public ImportPreviewResp preview(@RequestParam String type,
        @RequestPart("file") MultipartFile file) {
        return importService.preview(ImportType.of(type), file);
    }

    /**
     * 执行导入
     *
     * @param type   导入类型
     * @param dryRun 是否只校验
     * @param file   文件
     * @return 导入报告
     */
    @Operation(summary = "执行导入", description = "支持 Excel/CSV，逐行校验并返回带行号的错误明细（FR-IO-01）")
    @SaCheckPermission("monitor:io:import")
    @Log
    @PostMapping("/import/execute")
    public ImportReportResp execute(@RequestParam String type,
        @RequestParam(defaultValue = "false") boolean dryRun,
        @RequestPart("file") MultipartFile file) {
        return importService.execute(ImportType.of(type), file, dryRun);
    }

    /**
     * 流式导出历史数据 CSV
     *
     * @param deviceId  设备 ID
     * @param nodeId    节点号
     * @param analogNo  模拟量序号
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @param encoding  导出编码（可空= UTF-8；支持 GB18030，NFR-COMPAT-07）
     * @param response  响应
     * @throws IOException 写出失败
     */
    @Operation(summary = "导出历史数据 CSV",
        description = "按条件流式导出，内存占用与总行数无关（FR-IO-02）；encoding 支持 UTF-8/GB18030")
    @SaCheckPermission("monitor:io:export")
    @GetMapping("/export/data")
    public void exportData(@RequestParam(required = false) Long deviceId,
        @RequestParam(required = false) Integer nodeId,
        @RequestParam(required = false) Integer analogNo,
        @RequestParam(required = false) @DateTimeFormat(
            pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @RequestParam(required = false) @DateTimeFormat(
            pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
        @RequestParam(required = false) String encoding,
        HttpServletResponse response) throws IOException {
        Charset charset = ExportService.resolveCharset(encoding);
        response.setContentType("text/csv;charset=" + charset.name());
        response.setHeader("Content-Disposition", "attachment;filename=data.csv");
        exportService.exportDataCsv(deviceId, nodeId, analogNo, startTime, endTime, encoding,
            response
                .getOutputStream());
        response.flushBuffer();
    }

    /**
     * 导出单表 SQL
     *
     * @param table    表名
     * @param encoding 导出编码（可空= UTF-8；支持 GB18030，NFR-COMPAT-07）
     * @param response 响应
     * @throws IOException 写出失败
     */
    @Operation(summary = "导出单表 SQL",
        description = "业务表逻辑导出为 INSERT 语句（FR-IO-01/02）；encoding 支持 UTF-8/GB18030")
    @Parameter(name = "table", description = "业务表名", in = ParameterIn.QUERY)
    @SaCheckPermission("monitor:io:export")
    @GetMapping("/export/table")
    public void exportTable(@RequestParam String table,
        @RequestParam(required = false) String encoding, HttpServletResponse response)
        throws IOException {
        String name = sqlDumpService.requireTable(table);
        Charset charset = ExportService.resolveCharset(encoding);
        response.setContentType("text/plain;charset=" + charset.name());
        response.setHeader("Content-Disposition", "attachment;filename=" + name + ".sql");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(response
            .getOutputStream(), charset))) {
            sqlDumpService.dump(name, writer);
        }
        response.flushBuffer();
    }

    /**
     * 备份包列表
     *
     * @return 备份包列表
     */
    @Operation(summary = "备份包列表", description = "列出备份目录下的备份包（FR-IO-03）")
    @SaCheckPermission("monitor:io:backup")
    @GetMapping("/backup")
    public List<BackupFileResp> backups() {
        return backupService.list();
    }

    /**
     * 一键打包备份
     *
     * @param req 请求
     * @return 备份包信息
     */
    @Operation(summary = "一键打包备份", description = "打包配置、业务库、暂存库与日志并生成 SHA-256 清单（FR-IO-03）")
    @SaCheckPermission("monitor:io:backup")
    @Log
    @PostMapping("/backup")
    public BackupFileResp createBackup(@RequestBody(required = false) BackupCreateReq req) {
        BackupCreateReq request = req == null ? new BackupCreateReq() : req;
        boolean includeLogs =
            request.getIncludeLogs() == null ? backupProperties.isIncludeLogs() : request
                .getIncludeLogs();
        return backupService.create(request.getPassword(), includeLogs);
    }

    /**
     * 下载备份包
     *
     * @param name     文件名
     * @param response 响应
     * @throws IOException 写出失败
     */
    @Operation(summary = "下载备份包", description = "下载指定备份包（FR-IO-03）")
    @SaCheckPermission("monitor:io:backup")
    @GetMapping("/backup/download")
    public void downloadBackup(@RequestParam String name, HttpServletResponse response)
        throws IOException {
        Path file = backupService.resolve(name);
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(name,
            StandardCharsets.UTF_8));
        response.setContentLengthLong(Files.size(file));
        try (InputStream in = Files.newInputStream(file)) {
            StreamUtils.copy(in, response.getOutputStream());
        }
        response.flushBuffer();
    }

    /**
     * 校验备份包
     *
     * @param req 请求
     * @return 校验结果
     */
    @Operation(summary = "校验备份包", description = "逐条比对清单 SHA-256（FR-IO-03）")
    @SaCheckPermission("monitor:io:backup")
    @PostMapping("/backup/verify")
    public BackupVerifyResp verifyBackup(@RequestBody @Valid BackupPasswordReq req) {
        return backupService.verify(req.getName(), req.getPassword());
    }

    /**
     * 从备份包恢复
     *
     * @param req 请求
     * @return 恢复报告
     */
    @Operation(summary = "从备份包恢复", description = "校验通过后恢复，恢复前自动备份当前状态（FR-IO-04）")
    @SaCheckPermission("monitor:io:restore")
    @Log
    @PostMapping("/backup/restore")
    public BackupRestoreResp restore(@RequestBody @Valid BackupRestoreReq req) {
        return backupService.restore(req.getName(), req.getPassword(), Boolean.TRUE.equals(req
            .getOverride()));
    }

    /**
     * 清理过期备份
     *
     * @return 删除份数
     */
    @Operation(summary = "清理过期备份", description = "按保留份数删除最旧的备份包（FR-IO-05）")
    @SaCheckPermission("monitor:io:backup")
    @PostMapping("/backup/purge")
    public Integer purgeBackups() {
        return backupService.purge();
    }

    /**
     * 备份运行状态
     *
     * @return 状态
     */
    @Operation(summary = "备份运行状态", description = "返回定时备份开关、间隔、保留份数与最近执行结果（FR-IO-05）")
    @SaCheckPermission("monitor:io:backup")
    @GetMapping("/backup/status")
    public Map<String, Object> backupStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("dir", backupService.dir().toString());
        status.put("autoEnabled", backupProperties.isAutoEnabled());
        status.put("running", backupScheduler.enabled());
        status.put("intervalHours", backupProperties.getIntervalHours());
        status.put("keep", backupProperties.getKeep());
        status.put("includeLogs", backupProperties.isIncludeLogs());
        status.put("remoteDir", backupProperties.getRemoteDir());
        status.put("encryptedByDefault", backupProperties.getEncryptPassword() != null
            && !backupProperties.getEncryptPassword().isBlank());
        status.put("lastRunAt", backupScheduler.lastRunAt());
        status.put("lastResult", backupScheduler.lastResult());
        return status;
    }

    /**
     * 业务表清单（供导出选择）
     *
     * @return 表名列表
     */
    @Operation(summary = "业务表清单", description = "返回可导出的业务表（FR-IO-02）")
    @SaCheckPermission("monitor:io:export")
    @GetMapping("/tables")
    public List<String> tables() {
        return sqlDumpService.listTables();
    }
}
