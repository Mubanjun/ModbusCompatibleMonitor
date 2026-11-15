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

import cn.idev.excel.EasyExcel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 表格文件解析（FR-IO-01：Excel / CSV）
 *
 * <p>统一产出「表头 + 行 Map」，后续导入逻辑与文件格式无关。CSV 支持双引号包裹与转义，
 * Excel 走 FastExcel 的 {@code Map<Integer, String>} 读取，避免为每种表定义实体类。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
public final class TabularReader {

    /** 单次导入最大行数（防止误传超大文件打满内存） */
    public static final int MAX_ROWS = 200_000;

    private TabularReader() {
    }

    /**
     * 解析上传的表格文件
     *
     * @param fileName 文件名（用于判断格式）
     * @param in       输入流
     * @return 表格数据
     * @throws IOException 读取失败
     */
    public static TabularData read(String fileName, InputStream in) throws IOException {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            return readExcel(in);
        }
        return readCsv(in);
    }

    private static TabularData readCsv(InputStream in) throws IOException {
        List<String> headers = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stripBom(in),
            StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("文件为空");
            }
            headers.addAll(parseCsvLine(line));
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    throw new IOException("单次导入最多 " + MAX_ROWS + " 行，请拆分文件");
                }
                rows.add(toMap(headers, parseCsvLine(line)));
            }
        }
        return new TabularData(headers, rows);
    }

    private static TabularData readExcel(InputStream in) throws IOException {
        List<Map<Integer, String>> all = EasyExcel.read(in).headRowNumber(0).sheet().doReadSync();
        if (all == null || all.isEmpty()) {
            throw new IOException("文件为空");
        }
        List<String> headers = new ArrayList<>();
        Map<Integer, String> head = all.get(0);
        for (int i = 0; i < head.size(); i++) {
            headers.add(head.get(i) == null ? "" : head.get(i).trim());
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < all.size(); i++) {
            if (rows.size() >= MAX_ROWS) {
                throw new IOException("单次导入最多 " + MAX_ROWS + " 行，请拆分文件");
            }
            rows.add(toMap(headers, all.get(i)));
        }
        return new TabularData(headers, rows);
    }

    private static Map<String, String> toMap(List<String> headers, Map<Integer, String> row) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String value = row == null ? null : row.get(i);
            map.put(headers.get(i), value == null ? null : value.trim());
        }
        return map;
    }

    private static Map<String, String> toMap(List<String> headers, List<String> values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String value = i < values.size() ? values.get(i) : null;
            map.put(headers.get(i), value == null ? null : value.trim());
        }
        return map;
    }

    /**
     * 解析一行 CSV（支持双引号包裹与 "" 转义）
     *
     * @param line 行文本
     * @return 字段列表
     */
    public static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (quoted) {
                if (current == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(current);
                }
            } else if (current == '"') {
                quoted = true;
            } else if (current == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (current != '\r') {
                field.append(current);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    private static InputStream stripBom(InputStream in) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(in, 3);
        byte[] bom = new byte[3];
        int read = pushback.read(bom, 0, 3);
        if (read != 3 || (bom[0] & 0xFF) != 0xEF || (bom[1] & 0xFF) != 0xBB
            || (bom[2] & 0xFF) != 0xBF) {
            if (read > 0) {
                pushback.unread(bom, 0, read);
            }
        }
        return pushback;
    }
}
