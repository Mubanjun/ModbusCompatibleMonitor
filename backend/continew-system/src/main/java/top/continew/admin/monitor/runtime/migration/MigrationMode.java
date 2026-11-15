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

package top.continew.admin.monitor.runtime.migration;

import java.util.Locale;

/**
 * 迁移运行模式（FR-MIG-01 并存与切换）
 *
 * @author jdrk
 * @since 2026/9/24
 */
public enum MigrationMode {

    /** 仅本系统运行（默认） */
    OFF,
    /** 与仁科原软件并存：按错峰窗口暂停本系统采集 */
    COEXIST,
    /** 已切换到本系统 */
    SWITCHED;

    /**
     * 解析模式
     *
     * @param value 文本
     * @return 模式
     */
    public static MigrationMode of(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的迁移模式：" + value);
        }
    }
}
