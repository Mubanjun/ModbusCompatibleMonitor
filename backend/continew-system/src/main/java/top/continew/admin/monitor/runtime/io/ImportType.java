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

import java.util.Locale;

/**
 * 导入数据类型（FR-IO-01）
 *
 * @author jdrk
 * @since 2026/9/24
 */
public enum ImportType {

    /** 设备清单（含端口归属） */
    DEVICE,
    /** 历史数据 */
    HISTORY,
    /** 业务系统配置 */
    CONFIG;

    /**
     * 解析导入类型
     *
     * @param value 类型文本
     * @return 导入类型
     */
    public static ImportType of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("导入类型不能为空（DEVICE / HISTORY / CONFIG）");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的导入类型：" + value);
        }
    }
}
