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
 * 采集顺序实体
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@TableName("biz_collect_order")
public class BizCollectOrderDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属端口 ID
     */
    private Long portId;

    /**
     * 设备 ID
     */
    private Long deviceId;

    /**
     * 采集顺序
     */
    private Integer sortNo;

    /**
     * 是否参与轮询
     */
    private Boolean enabled;
}
