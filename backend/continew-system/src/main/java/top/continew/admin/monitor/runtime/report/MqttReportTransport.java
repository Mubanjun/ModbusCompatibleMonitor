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

package top.continew.admin.monitor.runtime.report;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 上报（FR-RPT-01）。
 *
 * <p>连接参数：`host`、`port`、`clientId`、`username`、`password`、`topic`、
 * `protocolVersion`（3 表示 MQTT 3.1.1，5 表示 MQTT 5.0）、`tls`（true 时使用 ssl://）。</p>
 *
 * <p>连接按「目标」缓存复用，同一目标连续上报不重复握手；发送失败会重建连接后交由引擎重试。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttReportTransport implements ReportTransport {

    private final ReportProperties properties;
    private final Map<Long, MqttClient> clients = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return "MQTT";
    }

    @Override
    public void send(ReportTransmission transmission) throws Exception {
        ReportTargetSpec target = transmission.target();
        JSONObject config = JSONUtil.parseObj(target.configJson());
        if (config.getInt("protocolVersion", 3) >= 5) {
            throw new IllegalStateException(
                "MQTT 5.0 通道未启用，请在目标配置中将 protocolVersion 设为 3 或启用 mqttv5 客户端");
        }
        try {
            MqttClient client = this.client(target, config);
            MqttMessage message = new MqttMessage(transmission.payload() == null ? new byte[0]
                : transmission.payload().getBytes(StandardCharsets.UTF_8));
            message.setQos(Math.min(Math.max(target.qos(), 0), 2));
            message.setRetained(target.retain());
            // 缺陷 61：主题模板会把设备名原样拼进来，而 MQTT 不允许发布主题含通配符 # / +（现场设备名就叫
            // 「现场-#1」）。这里做发布前的最后一道防线，避免整条通道因 Paho 抛异常而全部进死信。
            String topic = MqttTopics.normalize(transmission.topic());
            if (!topic.equals(transmission.topic())) {
                log.warn("MQTT 主题含通配符 # / +，已规范化为 [{}]（原主题 [{}]）", topic, transmission
                    .topic());
            }
            client.publish(topic, message);
        } catch (Exception e) {
            throw new IllegalStateException("MQTT 上报失败：" + describe(e), e);
        }
    }

    /**
     * 生成含原因码的错误说明（Paho 的 getMessage 只有类名，不便于排障）
     *
     * @param error 异常
     * @return 说明
     */
    public static String describe(Throwable error) {
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 5) {
            if (builder.length() > 0) {
                builder.append(" <- ");
            }
            if (current instanceof org.eclipse.paho.client.mqttv3.MqttException mqtt) {
                builder.append("MqttException(reason=").append(mqtt.getReasonCode()).append(", ")
                    .append(mqtt.getMessage()).append(')');
            } else {
                builder.append(current.getClass().getSimpleName()).append(": ").append(current
                    .getMessage());
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    @Override
    public void close() {
        for (MqttClient client : clients.values()) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            } catch (Exception e) {
                log.debug("关闭 MQTT 连接失败：{}", e.getMessage());
            }
        }
        clients.clear();
    }

    /**
     * 取（或建立）目标对应的 MQTT 连接
     *
     * @param target 目标
     * @param config 连接参数
     * @return MQTT 客户端
     */
    private MqttClient client(ReportTargetSpec target, JSONObject config) throws Exception {
        MqttClient cached = clients.get(target.id());
        if (cached != null && cached.isConnected()) {
            return cached;
        }
        synchronized (this) {
            cached = clients.get(target.id());
            if (cached != null && cached.isConnected()) {
                return cached;
            }
            if (cached != null) {
                try {
                    cached.close();
                } catch (Exception ignored) {
                    // 旧连接已失效，忽略关闭异常
                }
            }
            MqttClient client = this.connect(target, config);
            clients.put(target.id(), client);
            return client;
        }
    }

    private MqttClient connect(ReportTargetSpec target, JSONObject config) throws Exception {
        String host = config.getStr("host", "127.0.0.1");
        int port = config.getInt("port", 1883);
        boolean tls = config.getBool("tls", false);
        String serverUri = (tls ? "ssl://" : "tcp://") + host + ":" + port;
        String clientId = config.getStr("clientId", "jdrk-report-" + target.id());
        MqttClient client = new MqttClient(serverUri, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(properties.getMqttConnectTimeoutSeconds());
        options.setAutomaticReconnect(false);
        options.setKeepAliveInterval(30);
        if (config.containsKey("username")) {
            options.setUserName(config.getStr("username"));
        }
        if (config.containsKey("password")) {
            options.setPassword(config.getStr("password").toCharArray());
        }
        client.connect(options);
        return client;
    }
}
