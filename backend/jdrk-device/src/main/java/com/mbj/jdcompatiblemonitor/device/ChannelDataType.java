/*
 * Copyright (c) 2026-present 仁科兼容环境监测平台 Authors. All Rights Reserved.
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

package com.mbj.jdcompatiblemonitor.device;

/** 主机通道参数里的「数据属性」。 */
public enum ChannelDataType {

    /** 模拟量1禁用 / 模拟量2启用：只取寄存器 0x0000，0x0001 为无效值。 */
    ANALOG2_ONLY("模拟量1禁用模拟量2启用"),

    /** 模拟量1启用 / 模拟量2禁用：只取寄存器 0x0001。 */
    ANALOG1_ONLY("模拟量1启用模拟量2禁用"),

    /** 两个模拟量都启用：0x0000 = 模拟量2，0x0001 = 模拟量1。 */
    BOTH("模拟量1启用模拟量2启用"),

    /** 浮点型数据：0x0000 与 0x0001 组成 32 位大端浮点。 */
    FLOAT32("浮点型数据");

    private final String hostLabel;

    ChannelDataType(String hostLabel) {
        this.hostLabel = hostLabel;
    }

    /**
     * 读取该数据属性所需的寄存器个数
     *
     * <p>{@link ChannelValueReader} 以寄存器数组下标 0 对应 0x0000、下标 1 对应 0x0001，
     * 因此「仅模拟量1」也必须读 2 个寄存器，否则拿不到模拟量1 所在的 0x0001。</p>
     *
     * @return 需要的寄存器个数
     */
    public int requiredRegisterCount() {
        return switch (this) {
            case ANALOG2_ONLY -> 1;
            case ANALOG1_ONLY, BOTH, FLOAT32 -> 2;
        };
    }

    /** 主机配置软件中显示的中文名称。 */
    public String hostLabel() {
        return hostLabel;
    }

    /** 按主机配置软件中的中文名称反查枚举。 */
    public static ChannelDataType fromHostLabel(String label) {
        for (ChannelDataType type : values()) {
            if (type.hostLabel.equals(label)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown channel data type: " + label);
    }
}
