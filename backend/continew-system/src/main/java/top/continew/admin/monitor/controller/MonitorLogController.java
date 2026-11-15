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
import org.springframework.util.StreamUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.monitor.model.req.LogLevelReq;
import top.continew.admin.monitor.runtime.log.DiagnosticService;
import top.continew.admin.monitor.runtime.log.LogFileService;
import top.continew.admin.monitor.runtime.log.LogLevelService;
import top.continew.admin.monitor.runtime.model.LogFileResp;
import top.continew.admin.monitor.runtime.model.LogLevelResp;
import top.continew.starter.log.annotation.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 日志与诊断 API（FR-LOG-01/03/04）
 *
 * <p>提供日志级别在线切换、日志文件浏览下载与诊断包导出。日志本身按模块自动分文件，
 * 由 {@code logback-spring.xml} 完成轮转与占用上限控制。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Tag(name = "日志与诊断 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor")
public class MonitorLogController {

    private final LogLevelService logLevelService;
    private final LogFileService logFileService;
    private final DiagnosticService diagnosticService;

    /**
     * 查询受管日志器级别
     *
     * @return 级别列表
     */
    @Operation(summary = "查询日志级别", description = "返回平台受管日志器的当前生效级别（FR-LOG-01）")
    @SaCheckPermission("monitor:log:level")
    @GetMapping("/log/level")
    public List<LogLevelResp> levels() {
        return logLevelService.list();
    }

    /**
     * 在线切换日志级别
     *
     * @param req 请求参数
     * @return 切换后的级别
     */
    @Operation(summary = "切换日志级别", description = "在线切换指定日志器级别，立即生效且无需重启（FR-LOG-01）")
    @SaCheckPermission("monitor:log:level")
    @Log
    @PutMapping("/log/level")
    public LogLevelResp setLevel(@RequestBody @Valid LogLevelReq req) {
        return logLevelService.set(req.getLogger(), req.getLevel());
    }

    /**
     * 日志文件列表
     *
     * @return 日志文件列表
     */
    @Operation(summary = "日志文件列表", description = "列出日志目录下的 .log 与轮转后的 .gz（FR-LOG-03）")
    @SaCheckPermission("monitor:log:file")
    @GetMapping("/log/file")
    public List<LogFileResp> files() {
        return logFileService.list();
    }

    /**
     * 下载日志文件
     *
     * @param path     相对日志根目录的路径
     * @param response 响应
     * @throws IOException 读写失败
     */
    @Operation(summary = "下载日志文件", description = "下载指定日志文件，含目录逃逸与大小校验（FR-LOG-03）")
    @Parameter(name = "path", description = "相对日志根目录的路径", in = ParameterIn.QUERY)
    @SaCheckPermission("monitor:log:file")
    @GetMapping("/log/file/download")
    public void download(@RequestParam String path, HttpServletResponse response)
        throws IOException {
        Path file = logFileService.resolve(path);
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(file
            .getFileName().toString(), StandardCharsets.UTF_8));
        response.setContentLengthLong(Files.size(file));
        try (InputStream in = Files.newInputStream(file)) {
            StreamUtils.copy(in, response.getOutputStream());
        }
        response.flushBuffer();
    }

    /**
     * 导出诊断包
     *
     * @param response 响应
     * @throws IOException 读写失败
     */
    @Operation(summary = "导出诊断包", description = "打包日志、脱敏配置与运行时指标，附 SHA-256 清单（FR-LOG-04）")
    @SaCheckPermission("monitor:diagnostic:export")
    @Log
    @GetMapping("/diagnostic/package")
    public void diagnosticPackage(HttpServletResponse response) throws IOException {
        Path file = diagnosticService.create();
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment;filename=" + file.getFileName());
        response.setContentLengthLong(Files.size(file));
        try (InputStream in = Files.newInputStream(file)) {
            StreamUtils.copy(in, response.getOutputStream());
        }
        response.flushBuffer();
    }
}
