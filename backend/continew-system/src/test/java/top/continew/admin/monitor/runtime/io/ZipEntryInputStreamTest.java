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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 备份恢复的流式读取边界测试（FR-IO-05 / 审计观察项 3）。 */
class ZipEntryInputStreamTest {

    @Test
    void readingEntryStopsAtBoundaryAndNextEntryStillReadable() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("db/first.sql"));
            zip.write("INSERT INTO first VALUES (1);\nINSERT INTO first VALUES (2);\n"
                .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("db/second.sql"));
            zip.write("INSERT INTO second VALUES (9);\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(buffer.toByteArray()),
            StandardCharsets.UTF_8)) {
            ZipEntry first = zip.getNextEntry();
            assertEquals("db/first.sql", first.getName());
            BufferedReader reader =
                new BufferedReader(new InputStreamReader(new ZipEntryInputStream(
                    zip), StandardCharsets.UTF_8));
            assertEquals("INSERT INTO first VALUES (1);", reader.readLine());
            assertEquals("INSERT INTO first VALUES (2);", reader.readLine());
            // 条目边界处必须返回 null，而不是读到下一个条目的内容
            assertNull(reader.readLine());
            ZipEntry second = zip.getNextEntry();
            assertEquals("db/second.sql", second.getName());
            BufferedReader secondReader = new BufferedReader(new InputStreamReader(
                new ZipEntryInputStream(zip), StandardCharsets.UTF_8));
            assertEquals("INSERT INTO second VALUES (9);", secondReader.readLine());
        }
    }
}
