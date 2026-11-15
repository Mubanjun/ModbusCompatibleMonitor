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

import com.mbj.jdcompatiblemonitor.protocol.transport.SerialTransport;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 协议传输参数（FR-PROTO-07）
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Data
@Component
@ConfigurationProperties(prefix = "jdrk.protocol")
public class ProtocolProperties {

    /**
     * 串口帧间静默（毫秒）：RS-485 半双工总线两次发送之间的最小间隔
     */
    private int serialFrameGapMs = 5;

    /**
     * 把配置应用到串口传输层
     */
    @PostConstruct
    public void apply() {
        SerialTransport.setFrameGapMillis(serialFrameGapMs);
    }
}
