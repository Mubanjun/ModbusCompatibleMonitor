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

package top.continew.admin.monitor.runtime.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.runtime.buffer.StagingBufferService;
import top.continew.admin.monitor.runtime.engine.AcquisitionEngine;
import top.continew.admin.monitor.runtime.report.ReportEngine;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 诊断包导出（FR-LOG-04：日志 + 配置 + 指标，脱敏，可离线分析）。
 *
 * <p>打包内容：`logs/` 下的日志（含轮转后的压缩包）、脱敏后的 `config/application.yml`、
 * `metrics/runtime.json`（JVM、采集、暂存、上报运行态）以及 `manifest.json`（每个条目的
 * SHA-256 与打包时间），便于离线核对完整性。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosticService {

    /**
     * 配置中的敏感键（键名包含其一即把「值」替换为 ***）。
     *
     * <p>按行处理而不是整段正则：正则里的 \\s 会跨越换行，曾把 YAML 列表项连同
     * 空值父键一起吃掉。</p>
     */
    private static final List<String> SECRET_KEYS = List.of("password", "pwd", "secret", "token",
        "access-key", "secret-key", "private-key");

    /** 打包时间后缀 */
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 诊断包内说明文件 */
    private static final String README = """
        诊断包说明（FR-LOG-04）
        ========================
        logs/                 运行日志（含按模块拆分的日志与轮转后的 .gz）
        config/application.yml 运行配置（password/secret/token 等已脱敏为 ***）
        metrics/runtime.json  运行时指标（JVM、采集、暂存、上报）
        manifest.json         每个条目的字节数与 SHA-256，用于校验完整性

        生成方式：平台「日志与诊断」页面导出，或 GET /monitor/diagnostic/package。
        离线分析建议先核对 manifest.json 中的 sha256，再按时间定位日志。
        """;

    private static final String CONFIG_RESOURCE = "config/application.yml";

    private final LogProperties logProperties;
    private final AcquisitionEngine acquisitionEngine;
    private final StagingBufferService stagingBufferService;
    private final ReportEngine reportEngine;
    private final ObjectMapper objectMapper;

    /**
     * 生成诊断包
     *
     * @return 诊断包文件路径（保留在日志目录的 diagnostic 子目录下）
     */
    public Path create() {
        Path base = Paths.get(logProperties.getPath()).toAbsolutePath().normalize();
        String name = "diagnostic-" + LocalDateTime.now().format(STAMP) + ".zip";
        Path target = base.resolve(logProperties.getDiagnosticDir()).resolve(name);
        Map<String, String> digests = new LinkedHashMap<>();
        try {
            Files.createDirectories(target.getParent());
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files
                .newOutputStream(target)))) {
                zip.setLevel(9);
                this.writeLogs(zip, base, digests);
                this.writeEntry(zip, "config/application.yml", this.maskedConfig().getBytes(
                    StandardCharsets.UTF_8), digests);
                this.writeEntry(zip, "metrics/runtime.json", this.pretty(this.metrics()), digests);
                this.writeEntry(zip, "README.txt", README.getBytes(StandardCharsets.UTF_8),
                    digests);
                this.writeEntry(zip, "manifest.json", this.pretty(this.manifest(name, digests)),
                    digests);
            }
            log.info("诊断包已生成：{}（{} 个条目）", target, digests.size());
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("生成诊断包失败：" + e.getMessage(), e);
        }
    }

    /**
     * 脱敏后的运行配置文本（password/secret/token 等替换为 ***）
     *
     * @return 配置文本
     */
    public String maskedConfig() {
        try (InputStream in = new ClassPathResource(CONFIG_RESOURCE).getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String[] lines = text.split("\\n", -1);
            StringBuilder builder = new StringBuilder(text.length());
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    builder.append('\n');
                }
                builder.append(this.maskLine(lines[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            return "# 读取 " + CONFIG_RESOURCE + " 失败：" + e.getMessage();
        }
    }

    /**
     * 对单行配置做脱敏：只替换敏感键的「值」，空值父键（YAML 列表/嵌套结构）保持原样
     *
     * @param raw 原始行
     * @return 脱敏后的行
     */
    private String maskLine(String raw) {
        String trimmed = raw.stripLeading();
        if (trimmed.startsWith("#")) {
            return raw;
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return raw;
        }
        String key = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        if (SECRET_KEYS.stream().noneMatch(key::contains)) {
            return raw;
        }
        String rest = trimmed.substring(colon + 1);
        if (rest.isBlank()) {
            return raw;
        }
        String ending = rest.substring(rest.stripTrailing().length());
        int indent = raw.length() - trimmed.length();
        return raw.substring(0, indent) + trimmed.substring(0, colon) + ": ***" + ending;
    }

    /**
     * 运行时指标快照
     *
     * @return 指标
     */
    public Map<String, Object> metrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("javaVersion", System.getProperty("java.version"));
        jvm.put("javaVendor", System.getProperty("java.vendor"));
        jvm.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        jvm.put("startTime", runtime.getStartTime());
        jvm.put("uptimeMs", runtime.getUptime());
        jvm.put("heapUsedBytes", memory.getHeapMemoryUsage().getUsed());
        jvm.put("heapMaxBytes", memory.getHeapMemoryUsage().getMax());
        jvm.put("nonHeapUsedBytes", memory.getNonHeapMemoryUsage().getUsed());
        jvm.put("threadCount", threads.getThreadCount());
        jvm.put("peakThreadCount", threads.getPeakThreadCount());
        metrics.put("jvm", jvm);
        metrics.put("generatedAt", LocalDateTime.now().toString());
        try {
            metrics.put("acquisition", acquisitionEngine.snapshot());
        } catch (Exception e) {
            metrics.put("acquisitionError", e.getMessage());
        }
        try {
            metrics.put("staging", stagingBufferService.status());
        } catch (Exception e) {
            metrics.put("stagingError", e.getMessage());
        }
        try {
            metrics.put("report", reportEngine.status());
        } catch (Exception e) {
            metrics.put("reportError", e.getMessage());
        }
        return metrics;
    }

    private void writeLogs(ZipOutputStream zip, Path base, Map<String, String> digests)
        throws IOException {
        if (!Files.isDirectory(base)) {
            return;
        }
        long maxEntry = (long) logProperties.getMaxEntryMb() * 1024L * 1024L;
        long maxTotal = (long) logProperties.getMaxLogTotalMb() * 1024L * 1024L;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(base, 3)) {
            stream.filter(Files::isRegularFile).filter(this::isLogFile).forEach(files::add);
        }
        files.sort((left, right) -> {
            try {
                return Long.compare(Files.getLastModifiedTime(right).toMillis(), Files
                    .getLastModifiedTime(left).toMillis());
            } catch (IOException e) {
                return 0;
            }
        });
        long total = 0L;
        for (Path file : files) {
            long size = Files.size(file);
            if (size > maxEntry || total + size > maxTotal) {
                continue;
            }
            total += size;
            // 缺陷 69：日志条目必须**流式**写入。原先 Files.readAllBytes 会把整个文件读进堆，
            // 单条上限 50MB、总量 200MB 时峰值可达 400MB+（-Xmx1g、工作集已 800MB+），
            // 恰恰在「日志洪水」这种最需要诊断包的场景 OOM；备份路径早就是流式 copy。
            String entryName = "logs/" + base.relativize(file).toString().replace('\\', '/');
            ZipEntry entry = new ZipEntry(entryName);
            entry.setTime(System.currentTimeMillis());
            zip.putNextEntry(entry);
            MessageDigest digest = newSha256();
            DigestOutputStream sink = new DigestOutputStream(zip, digest);
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                in.transferTo(sink);
            }
            sink.flush();
            zip.closeEntry();
            digests.put(entryName, HexFormat.of().formatHex(digest.digest()));
        }
    }

    private void writeEntry(ZipOutputStream zip, String name, byte[] content,
        Map<String, String> digests) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(System.currentTimeMillis());
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
        digests.put(name, sha256(content));
    }

    private boolean isLogFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".log") || name.endsWith(".gz");
    }

    private byte[] pretty(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        } catch (Exception e) {
            return ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }

    private Map<String, Object> manifest(String name, Map<String, String> digests) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("package", name);
        manifest.put("createdAt", LocalDateTime.now().toString());
        manifest.put("algorithm", "SHA-256");
        // 清单自身也是包内条目，因此条目数要加 1
        manifest.put("entryCount", digests.size() + 1);
        manifest.put("entries", new LinkedHashMap<>(digests));
        return manifest;
    }

    private static String sha256(byte[] content) {
        return HexFormat.of().formatHex(newSha256().digest(content));
    }

    /**
     * 新建 SHA-256 摘要器（流式写条目时边写边算，避免把整个文件读进堆）
     *
     * @return 摘要器
     */
    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256：" + e.getMessage(), e);
        }
    }
}
