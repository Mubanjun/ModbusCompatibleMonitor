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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.runtime.model.BackupFileResp;
import top.continew.admin.monitor.runtime.model.BackupRestoreResp;
import top.continew.admin.monitor.runtime.model.BackupVerifyResp;
import top.continew.starter.core.util.validation.CheckUtils;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 一键打包备份与恢复（FR-IO-03/04/05/07）。
 *
 * <p>备份包结构：`db/表名.sql`（业务库逻辑导出）、`buffer/暂存库文件名`、
 * `logs/`（可选）、`config/application.yml`（脱敏）、`manifest.json`（行数 + SHA-256 清单）。
 * 加密包为 `*.zip.enc`：文件头 JDRKBK1 + 16 字节盐 + 12 字节 IV + AES-256-GCM 密文，
 * 密钥由口令经 PBKDF2-HMAC-SHA256（12 万次）派生。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final BackupProperties backupProperties;
    private final SqlDumpService sqlDumpService;
    private final ObjectMapper objectMapper;
    private final BackupPackageSupport packageSupport;

    @Value("${jdrk.staging.database-path:data/buffer/jdrk-buffer.db}")
    private String stagingDatabasePath;

    /**
     * 一键打包备份（FR-IO-03）
     *
     * @param password    加密口令，为空则生成明文包
     * @param includeLogs 是否包含日志
     * @return 备份包信息
     */
    public BackupFileResp create(String password, boolean includeLogs) {
        long start = System.currentTimeMillis();
        Path dir = this.dir();
        try {
            Files.createDirectories(dir);
            String baseName = "jdrk-backup-" + LocalDateTime.now().format(STAMP);
            boolean encrypted = password != null && !password.isBlank();
            Path target = dir.resolve(encrypted ? baseName + ".zip.enc" : baseName + ".zip");
            Map<String, String> digests = new LinkedHashMap<>();
            Map<String, Object> counters = new LinkedHashMap<>();
            try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(target))) {
                OutputStream sink = fileOut;
                if (encrypted) {
                    BackupPackageSupport.Secrets secrets = packageSupport.writeHeader(fileOut);
                    Cipher cipher = packageSupport.cipher(password, Cipher.ENCRYPT_MODE, secrets);
                    sink = new CipherOutputStream(fileOut, cipher);
                }
                try (ZipOutputStream zip = new ZipOutputStream(sink, StandardCharsets.UTF_8)) {
                    zip.setLevel(6);
                    for (String table : sqlDumpService.listTables()) {
                        this.writeEntry(zip, "db/" + table + ".sql", out -> {
                            // 必须显式 flush：OutputStreamWriter 的缓冲不属于 zip 条目流，
                            // 不刷会把每条脚本的尾部截断（曾导致恢复报「最后一条语句不完整」）
                            OutputStreamWriter writer = new OutputStreamWriter(out,
                                StandardCharsets.UTF_8);
                            sqlDumpService.dump(table, writer);
                            writer.flush();
                        }, digests);
                        counters.put(table, sqlDumpService.count(table));
                    }
                    Path buffer = Paths.get(stagingDatabasePath).toAbsolutePath().normalize();
                    if (Files.isRegularFile(buffer)) {
                        counters.put("bufferBytes", Files.size(buffer));
                        this.writeEntry(zip, "buffer/" + buffer.getFileName(),
                            out -> BackupPackageSupport
                                .copy(Files.newInputStream(buffer), out),
                            digests);
                    }
                    this.writeEntry(zip, "config/application.yml", out -> {
                        try (InputStream in = new ClassPathResource("config/application.yml")
                            .getInputStream()) {
                            BackupPackageSupport.copy(in, out);
                        }
                    }, digests);
                    if (includeLogs) {
                        this.writeLogs(zip, digests, counters);
                    }
                    Map<String, Object> manifest = new LinkedHashMap<>();
                    manifest.put("package", target.getFileName().toString());
                    manifest.put("createdAt", LocalDateTime.now().toString());
                    manifest.put("encrypted", encrypted);
                    manifest.put("algorithm", "SHA-256");
                    // 清单自身也是包内条目，因此条目数要加 1
                    manifest.put("entryCount", digests.size() + 1);
                    manifest.put("tableRows", counters);
                    manifest.put("entries", new LinkedHashMap<>(digests));
                    this.writeEntry(zip, "manifest.json", out -> out.write(this.pretty(manifest)),
                        digests);
                }
            }
            this.copyToRemote(target);
            this.purge();
            BackupFileResp resp = this.describe(target);
            log.info("备份完成：{}（{} 字节，加密={}，耗时 {}ms）", target.getFileName(), resp.getSize(),
                encrypted,
                System.currentTimeMillis() - start);
            return resp;
        } catch (IOException e) {
            throw new IllegalStateException("创建备份失败：" + e.getMessage(), e);
        }
    }

    /**
     * 列出备份包
     *
     * @return 备份包列表（新到旧）
     */
    public List<BackupFileResp> list() {
        Path dir = this.dir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<BackupFileResp> list = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).filter(this::isPackage)
                .forEach(path -> list.add(this
                    .describe(path)));
        } catch (IOException e) {
            throw new IllegalStateException("读取备份目录失败：" + e.getMessage(), e);
        }
        list.sort(Comparator.comparing(BackupFileResp::getCreatedAt, Comparator.nullsLast(Comparator
            .reverseOrder())));
        return list;
    }

    /**
     * 校验备份包完整性（FR-IO-03：备份包可校验）
     *
     * @param name     备份包文件名
     * @param password 加密口令
     * @return 校验结果
     */
    public BackupVerifyResp verify(String name, String password) {
        Path file = this.resolve(name);
        BackupVerifyResp resp = new BackupVerifyResp();
        resp.setName(name);
        resp.setEncrypted(packageSupport.isEncrypted(file));
        Path zipFile = file;
        Path temp = null;
        try {
            if (packageSupport.isEncrypted(file)) {
                temp = Files.createTempFile(this.dir(), "verify-", ".zip");
                packageSupport.decrypt(file, password, temp);
                zipFile = temp;
            }
            Map<String, String> content = new LinkedHashMap<>();
            try (ZipInputStream zip =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(
                    zipFile)), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    content.put(entry.getName(),
                        HexFormat.of().formatHex(BackupPackageSupport.sha256(zip)));
                }
            }
            resp.setEntries(content.size());
            if (!content.containsKey("manifest.json")) {
                resp.setValid(false);
                resp.setMismatched(List.of("manifest.json"));
                return resp;
            }
            Map<String, Object> manifest = this.readManifest(zipFile);
            @SuppressWarnings("unchecked")
            Map<String, String> entries = (Map<String, String>) manifest.get("entries");
            List<String> mismatched = new ArrayList<>();
            if (entries != null) {
                for (Map.Entry<String, String> item : entries.entrySet()) {
                    String actual = content.get(item.getKey());
                    if (actual == null || !actual.equalsIgnoreCase(item.getValue())) {
                        mismatched.add(item.getKey());
                    }
                }
                // 缺陷 67：反向也要查——包内**多出来**、manifest 未登记的条目必须报出来。
                // 恢复是遍历 zip 里全部 `db/*.sql` 执行的，只看 manifest→package 单向比对的话，
                // 往包里追加一个 `db/xxx.sql`（例如 `DELETE FROM biz_device;`）仍会返回 valid=true，
                // 恢复时却真的执行它——「校验通过」的结论不成立。
                for (String name2 : content.keySet()) {
                    if (!entries.containsKey(name2) && !"manifest.json".equals(name2)) {
                        mismatched.add(name2 + "(未登记)");
                    }
                }
            }
            resp.setChecked(entries == null ? 0 : entries.size());
            resp.setMismatched(mismatched);
            resp.setValid(mismatched.isEmpty());
            return resp;
        } catch (IOException e) {
            throw new IllegalStateException("校验备份包失败：" + e.getMessage(), e);
        } finally {
            packageSupport.deleteQuietly(temp);
        }
    }

    /**
     * 从备份包恢复（FR-IO-04：恢复前自动备份当前状态）
     *
     * @param name     备份包文件名
     * @param password 加密口令
     * @param override 是否覆盖（先清空目标表）
     * @return 恢复报告
     */
    public BackupRestoreResp restore(String name, String password, boolean override) {
        Path file = this.resolve(name);
        BackupVerifyResp verify = this.verify(name, password);
        CheckUtils.throwIf(!Boolean.TRUE.equals(verify.getValid()), "备份包校验未通过，已中止恢复：{}", verify
            .getMismatched());
        BackupRestoreResp resp = new BackupRestoreResp();
        resp.setName(name);
        resp.setOverride(override);
        BackupFileResp pre = this.create(null, false);
        resp.setPreBackup(pre.getName());
        Path zipFile = file;
        Path temp = null;
        int statements = 0;
        try {
            if (packageSupport.isEncrypted(file)) {
                temp = Files.createTempFile(this.dir(), "restore-", ".zip");
                packageSupport.decrypt(file, password, temp);
                zipFile = temp;
            }
            List<String> tables = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            try (ZipInputStream zip =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(
                    zipFile)), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entryName.startsWith("db/") && entryName.endsWith(".sql")) {
                        String table = entryName.substring(3, entryName.length() - 4);
                        // 流式解析：ZipEntryInputStream 在条目边界返回 -1，逐行读不会越界到下一个条目，
                        // 因此不必再把整张表脚本 readAllBytes() 进内存（千万行级的包会 OOM）。
                        // 注意不要关闭这个 reader：关闭会连带关闭底层 ZipInputStream。
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                            new ZipEntryInputStream(zip), StandardCharsets.UTF_8), 64 * 1024);
                        statements += sqlDumpService.restore(table, reader, override);
                        tables.add(table);
                    } else if (entryName.startsWith("buffer/")) {
                        Path target = Paths.get(stagingDatabasePath).toAbsolutePath().normalize();
                        Path restored = target.resolveSibling(target.getFileName() + ".restored");
                        if (restored.getParent() != null) {
                            Files.createDirectories(restored.getParent());
                        }
                        Files.copy(zip, restored, StandardCopyOption.REPLACE_EXISTING);
                        notes.add("暂存库已还原到 " + restored + "，需停采集后替换原文件并重启生效");
                    }
                }
            }
            resp.setTables(tables);
            resp.setStatements(statements);
            resp.setNotes(notes);
            log.warn("已从备份包 {} 恢复 {} 张表 / {} 条语句（override={}），恢复前备份：{}", name, tables.size(),
                statements, override, pre.getName());
            return resp;
        } catch (IOException e) {
            throw new IllegalStateException("从备份恢复失败：" + e.getMessage(), e);
        } finally {
            packageSupport.deleteQuietly(temp);
        }
    }

    /**
     * 校验包口令是否正确（FR-IO-07）
     *
     * @param name     备份包文件名
     * @param password 口令
     * @return 是否正确
     */
    public boolean checkPassword(String name, String password) {
        Path file = this.resolve(name);
        if (!packageSupport.isEncrypted(file)) {
            return true;
        }
        Path temp = null;
        try {
            temp = Files.createTempFile(this.dir(), "check-", ".zip");
            packageSupport.decrypt(file, password, temp);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            packageSupport.deleteQuietly(temp);
        }
    }

    /**
     * 按保留份数清理旧备份（FR-IO-05）
     *
     * @return 删除的份数
     */
    public int purge() {
        int keep = Math.max(1, backupProperties.getKeep());
        List<BackupFileResp> list = this.list();
        int removed = 0;
        for (int i = keep; i < list.size(); i++) {
            Path path = this.dir().resolve(list.get(i).getName()).normalize();
            packageSupport.deleteQuietly(path);
            removed++;
        }
        if (removed > 0) {
            log.info("备份清理：保留 {} 份，删除 {} 份", keep, removed);
        }
        return removed;
    }

    /**
     * 解析备份包路径（防目录逃逸）
     *
     * @param name 文件名
     * @return 绝对路径
     */
    public Path resolve(String name) {
        CheckUtils.throwIf(name == null || name.isBlank(), "备份包名称不能为空");
        Path dir = this.dir();
        Path file = dir.resolve(name).normalize();
        CheckUtils.throwIf(!file.startsWith(dir), "非法的备份包路径");
        CheckUtils.throwIf(!Files.isRegularFile(file), "备份包不存在：{}", name);
        return file;
    }

    /**
     * 备份目录
     *
     * @return 绝对路径
     */
    public Path dir() {
        return Paths.get(backupProperties.getDir()).toAbsolutePath().normalize();
    }

    private void copyToRemote(Path target) throws IOException {
        String remote = backupProperties.getRemoteDir();
        if (remote == null || remote.isBlank()) {
            return;
        }
        Path remoteDir = Paths.get(remote).toAbsolutePath().normalize();
        Files.createDirectories(remoteDir);
        Files.copy(target, remoteDir.resolve(target.getFileName()),
            StandardCopyOption.REPLACE_EXISTING);
        log.info("备份已复制到异地目录：{}", remoteDir);
    }

    private BackupFileResp describe(Path path) {
        BackupFileResp resp = new BackupFileResp();
        resp.setName(path.getFileName().toString());
        resp.setEncrypted(packageSupport.isEncrypted(path));
        try {
            resp.setSize(Files.size(path));
            resp.setCreatedAt(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(Files.getLastModifiedTime(
                    path).toMillis()), ZoneId.systemDefault()));
        } catch (IOException e) {
            log.debug("读取备份包信息失败：{}", e.getMessage());
        }
        return resp;
    }

    private boolean isPackage(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith("jdrk-backup-")
            && (name.endsWith(".zip") || name.endsWith(".zip.enc"));
    }

    private void writeLogs(ZipOutputStream zip, Map<String, String> digests,
        Map<String, Object> counters) throws IOException {
        Path logDir = Paths.get("./logs").toAbsolutePath().normalize();
        if (!Files.isDirectory(logDir)) {
            return;
        }
        long max = (long) backupProperties.getMaxLogMb() * 1024L * 1024L;
        long total = 0L;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(logDir, 3)) {
            stream.filter(Files::isRegularFile).forEach(files::add);
        }
        for (Path file : files) {
            long size = Files.size(file);
            if (total + size > max) {
                continue;
            }
            total += size;
            this.writeEntry(zip, "logs/" + logDir.relativize(file).toString().replace('\\', '/'),
                out -> BackupPackageSupport.copy(Files.newInputStream(file), out), digests);
        }
        counters.put("logBytes", total);
    }

    private void writeEntry(ZipOutputStream zip, String name, EntryWriter writer,
        Map<String, String> digests) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(System.currentTimeMillis());
        zip.putNextEntry(entry);
        MessageDigest digest = BackupPackageSupport.sha256();
        DigestOutputStream out = new DigestOutputStream(zip, digest);
        writer.write(out);
        out.flush();
        zip.closeEntry();
        digests.put(name, HexFormat.of().formatHex(digest.digest()));
    }

    private Map<String, Object> readManifest(Path zipFile) {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(
            zipFile)), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) {
                    return objectMapper.readValue(zip.readAllBytes(), new TypeReference<>() {
                    });
                }
            }
            return Map.of();
        } catch (IOException e) {
            throw new IllegalStateException("读取备份清单失败：" + e.getMessage(), e);
        }
    }

    private byte[] pretty(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化清单失败：" + e.getMessage(), e);
        }
    }

    /**
     * 备份条目写出函数
     */
    @FunctionalInterface
    private interface EntryWriter {

        /**
         * 写出条目内容
         *
         * @param out 输出流
         * @throws IOException 写出失败
         */
        void write(OutputStream out) throws IOException;
    }
}
