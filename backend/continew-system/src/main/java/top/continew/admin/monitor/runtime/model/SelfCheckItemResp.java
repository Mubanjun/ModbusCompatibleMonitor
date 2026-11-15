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

package top.continew.admin.monitor.runtime.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 自检项（FR-WD-06）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "自检项")
public class SelfCheckItemResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "检查项")
    private String name;

    @Schema(description = "是否通过")
    private Boolean ok;

    @Schema(description = "说明")
    private String detail;

    /**
     * 构造通过项
     *
     * @param name   名称
     * @param detail 说明
     * @return 检查项
     */
    public static SelfCheckItemResp pass(String name, String detail) {
        return new SelfCheckItemResp(name, true, detail);
    }

    /**
     * 构造失败项
     *
     * @param name   名称
     * @param detail 说明
     * @return 检查项
     */
    public static SelfCheckItemResp fail(String name, String detail) {
        return new SelfCheckItemResp(name, false, detail);
    }
}
