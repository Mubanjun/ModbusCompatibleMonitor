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
import lombok.Data;
import com.mbj.jdcompatiblemonitor.device.AnalogSample;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单模拟量的测试结果
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Data
@Schema(description = "单模拟量的测试结果")
public class AnalogTestResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模拟量序号（1 或 2）")
    private Integer analogNo;

    @Schema(description = "要素名称")
    private String elementName;

    @Schema(description = "原始值")
    private Double rawValue;

    @Schema(description = "工程值")
    private Double engineeringValue;

    @Schema(description = "单位")
    private String unit;

    /**
     * 从采集样本转换
     *
     * @param sample 模拟量样本
     * @param elementName 要素名称
     * @return 响应对象
     */
    public static AnalogTestResp of(AnalogSample sample, String elementName) {
        AnalogTestResp resp = new AnalogTestResp();
        resp.setAnalogNo(sample.analogNo());
        resp.setElementName(elementName);
        resp.setRawValue(sample.rawValue());
        resp.setEngineeringValue(sample.engineeringValue());
        resp.setUnit(sample.unit());
        return resp;
    }
}
