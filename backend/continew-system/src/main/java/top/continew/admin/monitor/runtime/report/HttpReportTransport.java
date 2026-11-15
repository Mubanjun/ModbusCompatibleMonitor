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
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP/Webhook 上报（FR-RPT-07，可选）。
 *
 * <p>连接参数：`url`（必填）、`contentType`（默认 application/json）、`token`（可选，作为 Bearer Token）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
public class HttpReportTransport implements ReportTransport {

    /**
     * 复用的 HTTP 客户端：JDK 的 HttpClient 自带 SelectorManager 线程与连接池，
     * 每个批次 new 一个且从不 close 会造成线程/连接反复创建（只能等 GC 的 Cleaner 回收）。
     * 按连接超时分桶缓存（不同目标可以配不同超时），容器关闭时统一释放。
     */
    private final Map<Long, HttpClient> clients = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return "HTTP";
    }

    private HttpClient client(long timeoutMs) {
        long connectTimeout = Math.max(timeoutMs, 100);
        return clients.computeIfAbsent(connectTimeout, millis -> HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(millis))
            .build());
    }

    /**
     * 释放所有 HTTP 客户端
     */
    @PreDestroy
    public void close() {
        clients.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭 HTTP 上报客户端失败：{}", e.getMessage());
            }
        });
        clients.clear();
    }

    @Override
    public void send(ReportTransmission transmission) throws Exception {
        ReportTargetSpec target = transmission.target();
        JSONObject config = JSONUtil.parseObj(target.configJson());
        String url = config.getStr("url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("HTTP 目标未配置 url");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(Math.max(target.timeoutMs(), 100)))
            .header("Content-Type", config.getStr("contentType", "application/json"))
            .POST(HttpRequest.BodyPublishers.ofString(transmission.payload() == null ? ""
                : transmission.payload(), StandardCharsets.UTF_8));
        String token = config.getStr("token");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = this.client(target.timeoutMs())
            .send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP 目标返回状态码 " + response.statusCode());
        }
    }
}
