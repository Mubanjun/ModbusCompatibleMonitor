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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/** 分组创建/修改请求。 */
@Data
public class BizDeviceGroupReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "分组名称不能为空")
    @Length(max = 50, message = "分组名称长度不能超过 {max} 个字符")
    private String name;

    private Long parentId;

    @Min(value = 0, message = "排序不能小于 {value}")
    @Max(value = 9999, message = "排序不能大于 {value}")
    private Integer sortNo;

    @Length(max = 200, message = "备注长度不能超过 {max} 个字符")
    private String remark;
}
