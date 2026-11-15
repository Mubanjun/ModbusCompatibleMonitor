/*
 * Copyright (c) 2026-present 仁科兼容环境监测平台 Authors. All Rights Reserved.
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

package com.mbj.jdcompatiblemonitor.protocol.transport;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * 设备传输通道。
 *
 * <p>抽象串口与 TCP 两种物理链路，协议实现只依赖本接口，
 * 因此新增链路类型不需要改动协议层。</p>
 */
public interface DeviceTransport extends Closeable {

    /**
     * 打开通道
     *
     * @throws IOException 打开失败
     */
    void open() throws IOException;

    /**
     * 通道是否已打开
     *
     * @return 是否已打开
     */
    boolean isOpen();

    /**
     * 写入数据
     *
     * @param data 待写数据
     * @throws IOException 写入失败
     */
    void write(byte[] data) throws IOException;

    /**
     * 读取恰好指定长度的数据
     *
     * @param expectedLength 期望字节数
     * @param timeoutMs      超时（毫秒）
     * @return 读到的数据
     * @throws IOException 读取失败或超时
     */
    byte[] readExactly(int expectedLength, int timeoutMs) throws IOException;

    /**
     * 清空接收缓冲（用于丢弃上一帧的残留数据）
     *
     * @throws IOException 操作失败
     */
    void purge() throws IOException;

    /**
     * 通道描述（用于日志与信令展示）
     *
     * @return 描述文本
     */
    String description();

    /**
     * 关闭通道。
     *
     * <p>刻意收窄 {@link Closeable#close()} 的签名：关闭失败对调用方无补救意义，
     * 不应迫使上层处理异常。</p>
     */
    @Override
    void close();

    /**
     * 从输入流读取恰好指定长度的数据
     *
     * @param in             输入流
     * @param expectedLength 期望字节数
     * @param timeoutMs      超时（毫秒）
     * @return 读到的数据
     * @throws IOException 读取失败或超时
     */
    static byte[] readFully(InputStream in, int expectedLength, int timeoutMs) throws IOException {
        byte[] buffer = new byte[expectedLength];
        int read = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (read < expectedLength) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new IOException("读取超时：期望 " + expectedLength + " 字节，实际仅收到 " + read + " 字节");
            }
            int available = in.available();
            if (available <= 0) {
                // 串口与 Socket 都缺少可靠的阻塞式超时读，这里用短睡眠轮询换取可控超时
                sleepQuietly(5);
                continue;
            }
            int n = in.read(buffer, read, Math.min(available, expectedLength - read));
            if (n < 0) {
                throw new IOException("通道已关闭：期望 " + expectedLength + " 字节，实际收到 " + read + " 字节");
            }
            read += n;
        }
        return buffer;
    }

    /**
     * 休眠且忽略中断
     *
     * @param millis 毫秒
     */
    static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
