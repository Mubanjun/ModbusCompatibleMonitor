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

package top.continew.admin.monitor.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.BaseDO;

import java.io.Serial;

/**
 * 上报规则实体
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_report_rule")
public class BizReportRuleDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 上报目标 ID
     */
    private Long targetId;

    /**
     * 上报模板 ID
     */
    private Long templateId;

    /**
     * 限定端口 ID（空表示全部）
     */
    private Long portId;

    /**
     * 限定设备 ID（空表示全部）
     */
    private Long deviceId;

    /**
     * 上报间隔（毫秒）
     */
    private Integer intervalMs;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 描述
     */
    private String description;
}
