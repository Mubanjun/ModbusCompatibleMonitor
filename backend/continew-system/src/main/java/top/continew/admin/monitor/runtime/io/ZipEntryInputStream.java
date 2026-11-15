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

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipInputStream;

/**
 * 只读到「当前 ZipEntry 结束」的输入流（FR-IO-05 备份恢复）。
 *
 * <p>{@link ZipInputStream#read} 在条目边界返回 -1，因此可以用它安全地包一层
 * {@code BufferedReader} 逐行解析，而不会读到下一个条目；
 * 恢复时因此不必再 `readAllBytes()` 把整张表的脚本读进内存（千万行级的包会 OOM）。</p>
 *
 * <p>注意：**不要关闭**包装出来的 reader——关闭会连带关闭底层 {@code ZipInputStream}，
 * 后续条目就读不到了。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
public final class ZipEntryInputStream extends InputStream {

    private final ZipInputStream zip;

    public ZipEntryInputStream(ZipInputStream zip) {
        this.zip = zip;
    }

    @Override
    public int read() throws IOException {
        return zip.read();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return zip.read(buffer, offset, length);
    }

    @Override
    public int available() throws IOException {
        return 0;
    }
}
