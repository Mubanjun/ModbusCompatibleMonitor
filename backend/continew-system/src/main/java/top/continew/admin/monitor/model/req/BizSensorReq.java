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

package top.continew.admin.monitor.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/** 传感器对象创建/修改请求。 */
@Data
@Schema(description = "传感器对象创建/修改请求")
public class BizSensorReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "传感器名称不能为空")
    @Length(max = 100, message = "传感器名称长度不能超过 {max} 个字符")
    private String name;

    @Schema(description = "所属分组 ID")
    private Long groupId;

    @NotNull(message = "所属端口不能为空")
    private Long portId;

    private Long deviceId;

    @Min(value = 1, message = "通道号范围为 1~32")
    @Max(value = 32, message = "通道号范围为 1~32")
    private Integer channelNo;

    @Min(value = 1, message = "模拟量序号为 1 或 2")
    @Max(value = 2, message = "模拟量序号为 1 或 2")
    private Integer analogNo;

    @NotBlank(message = "要素编码不能为空")
    @Length(max = 50, message = "要素编码长度不能超过 {max} 个字符")
    private String elementCode;

    @Length(max = 20, message = "单位长度不能超过 {max} 个字符")
    private String unit;

    @DecimalMin(value = "-99999999999999", message = "量程下限超出范围")
    private BigDecimal rangeMin;

    @DecimalMax(value = "99999999999999", message = "量程上限超出范围")
    private BigDecimal rangeMax;

    private BigDecimal coefficient;

    private BigDecimal offsetValue;

    @Min(value = 0, message = "精度不能小于 {value}")
    @Max(value = 9, message = "精度不能大于 {value}")
    private Integer precision;

    private Long templateId;

    @NotNull(message = "是否启用不能为空")
    private Boolean enabled;
}
