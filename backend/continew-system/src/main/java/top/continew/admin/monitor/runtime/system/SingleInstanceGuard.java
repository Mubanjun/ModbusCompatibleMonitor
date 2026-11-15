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

package top.continew.admin.monitor.runtime.system;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 单实例控制（FR-SYS-02）。
 *
 * <p>用排他文件锁保证同一台机器上只有一个后端进程：第二个实例会在启动阶段就拿到明确提示
 * （而不是等到端口被占用才失败）；进程异常退出时操作系统会自动释放锁，残留锁文件不影响下次启动。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SingleInstanceGuard {

    private final SystemProperties systemProperties;

    private FileChannel channel;

    private FileLock lock;

    /**
     * 启动时获取单实例锁
     */
    @PostConstruct
    public void acquire() {
        if (!systemProperties.isSingleInstance()) {
            log.warn("单实例保护未启用（jdrk.system.single-instance=false）");
            return;
        }
        Path path = Paths.get(systemProperties.getInstanceLockPath()).toAbsolutePath().normalize();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null;
        } catch (IOException e) {
            throw new IllegalStateException("获取单实例锁失败：" + e.getMessage(), e);
        }
        if (lock == null) {
            this.closeChannel();
            String message = "检测到本平台已有实例在运行（锁文件 " + path + "），本次启动已终止";
            log.error(message);
            // 只抛异常是不够的：Spring 启动失败后 JVM 可能被第三方客户端的非守护线程拖住，
            // 实测第二个实例会「打印终止提示后一直挂着」（既没服务又占着内存）。
            // 本次启动已注定失败，这里直接结束进程，不留僵尸实例。
            System.exit(1);
            throw new IllegalStateException(message);
        }
        try {
            channel.truncate(0);
            channel.write(ByteBuffer.wrap((ProcessHandle.current().pid() + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8)));
            channel.force(true);
        } catch (IOException e) {
            log.debug("写入单实例锁文件失败：{}", e.getMessage());
        }
        log.info("单实例锁已获取：{}（pid={}）", path, ProcessHandle.current().pid());
    }

    /**
     * 退出时释放锁
     */
    @PreDestroy
    public void release() {
        if (lock != null) {
            try {
                lock.release();
            } catch (IOException e) {
                log.debug("释放单实例锁失败：{}", e.getMessage());
            }
            lock = null;
        }
        this.closeChannel();
    }

    /**
     * 当前是否持有锁（供自检使用）
     *
     * @return 是否持有
     */
    public boolean locked() {
        return lock != null && lock.isValid();
    }

    private void closeChannel() {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException e) {
            log.debug("关闭单实例锁通道失败：{}", e.getMessage());
        }
        channel = null;
    }
}
