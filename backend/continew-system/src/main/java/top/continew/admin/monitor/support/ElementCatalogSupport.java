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

package top.continew.admin.monitor.support;

import com.mbj.jdcompatiblemonitor.device.ChannelDataType;
import com.mbj.jdcompatiblemonitor.device.ElementRange;
import com.mbj.jdcompatiblemonitor.device.ElementRangeCatalog;
import com.mbj.jdcompatiblemonitor.device.ElementType;
import com.mbj.jdcompatiblemonitor.protocol.ValueEncoding;
import top.continew.starter.core.util.validation.CheckUtils;

/**
 * 要素量程字典的查询支撑
 *
 * <p>通道配置与设备模板都要按「要素 + 量程档位」套用编码方式与系数，
 * 这里集中提供查表与校验，避免两处各写一份。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
public final class ElementCatalogSupport {

    private ElementCatalogSupport() {
    }

    /**
     * 按枚举名解析要素类型
     *
     * @param name 枚举名
     * @return 要素类型
     */
    public static ElementType resolveElementType(String name) {
        for (ElementType type : ElementType.values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        CheckUtils.throwIf(true, "未知的要素类型：[{}]", name);
        return null;
    }

    /**
     * 按档位说明在要素下匹配量程
     *
     * @param elementType 要素类型
     * @param rangeLabel  档位说明
     * @return 量程档位
     */
    public static ElementRange resolveRange(ElementType elementType, String rangeLabel) {
        for (ElementRange range : ElementRangeCatalog.rangesOf(elementType)) {
            if (range.label().equals(rangeLabel)) {
                return range;
            }
        }
        CheckUtils.throwIf(true, "要素 [{}] 不存在量程档位：[{}]", elementType.displayName(), rangeLabel);
        return null;
    }

    /**
     * 判断是否为合法的通道数据属性
     *
     * @param name 枚举名
     * @return 是否合法
     */
    public static boolean isValidDataType(String name) {
        for (ChannelDataType type : ChannelDataType.values()) {
            if (type.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为合法的值编码方式
     *
     * @param name 枚举名
     * @return 是否合法
     */
    public static boolean isValidEncoding(String name) {
        for (ValueEncoding encoding : ValueEncoding.values()) {
            if (encoding.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
