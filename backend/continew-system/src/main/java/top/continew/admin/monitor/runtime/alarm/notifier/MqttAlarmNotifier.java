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

package top.continew.admin.monitor.runtime.alarm.notifier;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.runtime.alarm.AlarmNotifier;
import top.continew.admin.monitor.runtime.report.MqttReportTransport;
import top.continew.admin.monitor.runtime.report.MqttTopics;
import top.continew.admin.monitor.runtime.service.RuntimeSettingsService;

import java.nio.charset.StandardCharsets;

/**
 * MQTT 报警通知通道（FR-ALM-04 的 MQTT 方式，与 FR-RPT-01 共用 MQTT 能力）。
 *
 * <p>Broker 与主题来自 ALARM 分类配置；未配置可用 Broker 时抛出可读原因，
 * 通知记录保持失败态并按策略重试，不会静默丢弃。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttAlarmNotifier implements AlarmNotifier {

    /** 默认客户端标识 */
    private static final String CLIENT_ID = "jdrk-alarm-notify";

    private final RuntimeSettingsService runtimeSettingsService;

    private volatile MqttClient client;
    private volatile String currentUri;

    @Override
    public String notifyType() {
        return "MQTT";
    }

    @Override
    public void send(String target, String content) throws Exception {
        String host = runtimeSettingsService.alarmTarget(RuntimeSettingsService.KEY_ALARM_MQTT_HOST,
            "127.0.0.1");
        int port = (int) runtimeSettingsService.storageLong(
            RuntimeSettingsService.KEY_ALARM_MQTT_PORT, 1883L);
        String configured =
            StrUtil.isNotBlank(target) ? target : runtimeSettingsService.alarmTarget(
                RuntimeSettingsService.KEY_ALARM_MQTT_TOPIC, "jdrk/alarm");
        // 发布主题不得含通配符 # / +（见缺陷 61）：报警主题同样做规范化，配置写错时不再让通知整条失效
        String topic = MqttTopics.normalize(configured);
        if (!topic.equals(configured)) {
            log.warn("报警 MQTT 主题含通配符 # / +，已规范化为 [{}]（原主题 [{}]）", topic, configured);
        }
        try {
            MqttClient publisher = this.client(host, port);
            MqttMessage message = new MqttMessage(content == null ? new byte[0] : content
                .getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            publisher.publish(topic, message);
        } catch (Exception e) {
            throw new IllegalStateException("MQTT 报警通知失败："
                + MqttReportTransport.describe(e), e);
        }
    }

    /**
     * 释放 MQTT 连接
     */
    @PreDestroy
    public void close() {
        MqttClient cached = client;
        client = null;
        if (cached == null) {
            return;
        }
        try {
            if (cached.isConnected()) {
                cached.disconnect();
            }
            cached.close();
        } catch (Exception e) {
            log.debug("关闭报警 MQTT 连接失败：{}", e.getMessage());
        }
    }

    private MqttClient client(String host, int port) throws Exception {
        String uri = "tcp://" + host + ":" + port;
        MqttClient cached = client;
        if (cached != null && cached.isConnected() && uri.equals(currentUri)) {
            return cached;
        }
        synchronized (this) {
            if (client != null && client.isConnected() && uri.equals(currentUri)) {
                return client;
            }
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {
                    // 旧连接已失效，忽略关闭异常
                }
            }
            MqttClient created = new MqttClient(uri, CLIENT_ID, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setAutomaticReconnect(false);
            String username = runtimeSettingsService.alarmTarget(
                RuntimeSettingsService.KEY_ALARM_MQTT_USERNAME, null);
            if (StrUtil.isNotBlank(username)) {
                options.setUserName(username);
                String password = runtimeSettingsService.alarmTarget(
                    RuntimeSettingsService.KEY_ALARM_MQTT_PASSWORD, "");
                options.setPassword(password.toCharArray());
            }
            created.connect(options);
            this.client = created;
            this.currentUri = uri;
            return created;
        }
    }
}
