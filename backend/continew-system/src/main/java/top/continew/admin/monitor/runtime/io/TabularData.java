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

import java.util.List;
import java.util.Map;

/**
 * 解析后的表格数据（首行为表头）
 *
 * @param headers 表头
 * @param rows    数据行（键为表头名）
 * @author jdrk
 * @since 2026/9/24
 */
public record TabularData(List<String> headers, List<Map<String, String>> rows) {
}
