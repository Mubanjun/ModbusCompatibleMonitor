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
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/** 设备位置更新请求。 */
@Data
@Schema(description = "设备位置更新请求")
public class BizDeviceLocationReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID（批量时必填）")
    private Long deviceId;

    @Schema(description = "安装位置")
    @Length(max = 200, message = "安装位置长度不能超过 {max} 个字符")
    private String location;

    @Schema(description = "经度 WGS-84")
    @DecimalMin(value = "-180", message = "经度范围为 -180~180")
    @DecimalMax(value = "180", message = "经度范围为 -180~180")
    private BigDecimal longitude;

    @Schema(description = "纬度 WGS-84")
    @DecimalMin(value = "-90", message = "纬度范围为 -90~90")
    @DecimalMax(value = "90", message = "纬度范围为 -90~90")
    private BigDecimal latitude;

    @Schema(description = "海拔（米）")
    @DecimalMin(value = "-500", message = "海拔超出合理范围")
    @DecimalMax(value = "9000", message = "海拔超出合理范围")
    private BigDecimal elevation;

    @Schema(description = "详细地址")
    @Length(max = 255, message = "地址长度不能超过 {max} 个字符")
    private String address;
}
