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

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.math.BigDecimal;
import lombok.Data;
import top.continew.admin.common.base.model.entity.BaseDO;

/** 传感器对象（一等公民）。 */
@Data
@TableName("biz_sensor")
public class BizSensorDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private Long groupId;
    private Long portId;
    private Long deviceId;
    private Integer channelNo;
    private Integer analogNo;
    private String elementCode;
    private String unit;
    private BigDecimal rangeMin;
    private BigDecimal rangeMax;
    private BigDecimal coefficient;
    private BigDecimal offsetValue;
    @TableField("`precision`")
    private Integer precision;
    private Long templateId;
    private Boolean enabled;
}
