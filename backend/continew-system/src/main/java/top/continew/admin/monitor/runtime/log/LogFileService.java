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

import cn.hutool.core.io.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.runtime.model.LogFileResp;
import top.continew.starter.core.util.validation.CheckUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 日志文件浏览与下载（FR-LOG-03/04）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogFileService {

    private final LogProperties logProperties;

    /**
     * 列出日志文件（.log 与轮转后的 .gz），按修改时间倒序
     *
     * @return 日志文件列表
     */
    public List<LogFileResp> list() {
        Path base = this.baseDir();
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        List<LogFileResp> list = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(base, 3)) {
            stream.filter(Files::isRegularFile).filter(this::isLogFile).forEach(path -> {
                LogFileResp resp = new LogFileResp();
                Path relative = base.relativize(path);
                resp.setPath(relative.toString().replace('\\', '/'));
                resp.setName(path.getFileName().toString());
                resp.setModule(this.moduleOf(relative));
                try {
                    long size = Files.size(path);
                    resp.setSize(size);
                    resp.setSizeLabel(FileUtil.readableFileSize(size));
                    resp.setLastModified(LocalDateTime.ofInstant(Instant.ofEpochMilli(Files
                        .getLastModifiedTime(path).toMillis()), ZoneId.systemDefault()));
                } catch (IOException e) {
                    log.debug("读取日志文件信息失败：{}", e.getMessage());
                }
                list.add(resp);
            });
        } catch (IOException e) {
            throw new IllegalStateException("遍历日志目录失败：" + e.getMessage(), e);
        }
        list.sort(Comparator.comparing(LogFileResp::getLastModified, Comparator.nullsLast(Comparator
            .reverseOrder())));
        return list;
    }

    /**
     * 解析日志文件路径，并做目录逃逸与大小校验
     *
     * @param relativePath 相对日志根目录的路径
     * @return 绝对路径
     */
    public Path resolve(String relativePath) {
        CheckUtils.throwIf(relativePath == null || relativePath.isBlank(), "日志路径不能为空");
        Path base = this.baseDir();
        Path file = base.resolve(relativePath).normalize();
        CheckUtils.throwIf(!file.startsWith(base), "非法的日志路径");
        CheckUtils.throwIf(!Files.isRegularFile(file), "日志文件不存在");
        try {
            long max = (long) logProperties.getMaxEntryMb() * 1024L * 1024L;
            CheckUtils.throwIf(Files.size(file) > max, "日志文件超过 %s MB，请直接查看服务端文件", logProperties
                .getMaxEntryMb());
        } catch (IOException e) {
            throw new IllegalStateException("读取日志文件大小失败：" + e.getMessage(), e);
        }
        return file;
    }

    /**
     * 日志根目录
     *
     * @return 规范化绝对路径
     */
    public Path baseDir() {
        return Paths.get(logProperties.getPath()).toAbsolutePath().normalize();
    }

    private boolean isLogFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".log") || name.endsWith(".gz");
    }

    private String moduleOf(Path relative) {
        int count = relative.getNameCount();
        if (count >= 2) {
            return relative.getName(0).toString();
        }
        String name = relative.getFileName().toString();
        int index = name.indexOf('.');
        return index > 0 ? name.substring(0, index) : name;
    }
}
