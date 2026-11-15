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

package top.continew.admin.monitor.runtime.engine;

import com.mbj.jdcompatiblemonitor.device.AnalogDefinition;
import com.mbj.jdcompatiblemonitor.device.ElementType;
import com.mbj.jdcompatiblemonitor.protocol.Protocols;
import top.continew.admin.monitor.runtime.session.ChannelBinding;
import top.continew.admin.monitor.runtime.session.PortSession;

import java.net.SocketTimeoutException;

/**
 * 采集相关的纯取值工具（模拟量定义/要素/端口类型/超时判定）
 *
 * <p>被采集内核与诊断视图共用，避免相同判断散落多处（NFR-MAINT-03）。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
public final class AcquisitionElements {

    private AcquisitionElements() {
    }

    /**
     * 取模拟量定义
     *
     * @param binding  通道绑定
     * @param analogNo 模拟量序号
     * @return 模拟量定义
     */
    public static AnalogDefinition analog(ChannelBinding binding, int analogNo) {
        return analogNo == 2 ? binding.config().analog2() : binding.config().analog1();
    }

    /**
     * 取要素类型
     *
     * @param binding  通道绑定
     * @param analogNo 模拟量序号
     * @return 要素类型
     */
    public static ElementType elementType(ChannelBinding binding, int analogNo) {
        return analog(binding, analogNo).elementType();
    }

    /**
     * 取要素名称
     *
     * @param binding  通道绑定
     * @param analogNo 模拟量序号
     * @return 要素名称
     */
    public static String elementName(ChannelBinding binding, int analogNo) {
        return analog(binding, analogNo).name();
    }

    /**
     * 是否为 TCP 端口
     *
     * @param port 端口会话
     * @return 是否为 TCP
     */
    public static boolean isTcp(PortSession port) {
        return Protocols.PORT_TYPE_TCP.equalsIgnoreCase(port.port().getPortType());
    }

    /**
     * 判断异常是否为通讯超时
     *
     * @param error 异常
     * @return 是否超时
     */
    public static boolean isTimeout(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 10) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("超时") || message.toLowerCase()
                .contains("timeout"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
