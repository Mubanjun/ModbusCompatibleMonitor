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

package top.continew.admin.watchdog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 管理器控制面（FR-WD-07）：只读状态查询 + 启停控制 + 日志尾部。
 *
 * <p>刻意做在管理器自己身上而不是后端里：后端挂掉时运维仍然要能「看到是谁挂了、把它拉起来」。
 * 默认只监听 127.0.0.1；配置了 {@code control.token} 时校验 {@code X-Watchdog-Token} 头，
 * 未配置时仅允许回环地址访问。</p>
 *
 * @author jdrk
 * @since 2026/11/5
 */
final class ControlServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProcessManager manager;

    private final String token;

    private final int maxLogLines;

    private HttpServer server;

    ControlServer(ProcessManager manager, String token, int maxLogLines) {
        this.manager = manager;
        this.token = token == null ? "" : token.trim();
        this.maxLogLines = maxLogLines;
    }

    /**
     * 启动控制面
     *
     * @param host 监听地址
     * @param port 监听端口
     * @throws IOException 绑定失败
     */
    void start(String host, int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.createContext("/pm", this::handle);
        this.server.setExecutor(Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "jdrk-pm-http");
            thread.setDaemon(true);
            return thread;
        }));
        this.server.start();
        this.manager.log("控制面已启动：http://" + host + ":" + port + "/pm/status（令牌"
            + (this.token.isBlank() ? "未配置，仅限回环" : "已启用") + "）");
    }

    /**
     * 停止控制面
     */
    void stop() {
        HttpServer local = this.server;
        this.server = null;
        if (local != null) {
            local.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!this.authorized(exchange)) {
                this.write(exchange, 403, envelope(403, "无权访问：缺少或错误的控制令牌", null));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String[] parts = path.split("/");
            // /pm/status | /pm/health | /pm/logs/{name} | /pm/service/{name}[/{action}]
            if (path.equals("/pm/status") || path.equals("/pm/status/")) {
                this.write(exchange, 200, envelope(0, "ok", this.manager.status()));
                return;
            }
            if (path.equals("/pm/health")) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("running", true);
                data.put("services", this.manager.services().size());
                this.write(exchange, 200, envelope(0, "ok", data));
                return;
            }
            if (parts.length >= 4 && "logs".equals(parts[2])) {
                this.write(exchange, 200, envelope(0, "ok", this.tail(parts[3])));
                return;
            }
            if (parts.length >= 4 && "service".equals(parts[2])) {
                String name = parts[3];
                ManagedService service = this.manager.service(name);
                if (service == null) {
                    this.write(exchange, 404, envelope(404, "服务不存在：" + name, null));
                    return;
                }
                if (parts.length == 4 && "GET".equalsIgnoreCase(method)) {
                    this.write(exchange, 200, envelope(0, "ok", service.snapshot()));
                    return;
                }
                if (parts.length == 5 && "POST".equalsIgnoreCase(method)) {
                    this.write(exchange, 200, this.control(parts[4], name));
                    return;
                }
            }
            this.write(exchange, 404, envelope(404, "未知接口：" + method + " " + path, null));
        } catch (Exception e) {
            this.write(exchange, 500, envelope(500, "控制面异常：" + e.getMessage(), null));
        } finally {
            exchange.close();
        }
    }

    private Map<String, Object> control(String action, String name) {
        switch (action) {
            case "start" -> {
                boolean ok = this.manager.submitStart(name);
                return envelope(ok ? 0 : 500, ok ? "已提交启动" : "启动失败",
                    this.manager.service(name).snapshot());
            }
            case "stop" -> {
                boolean ok = this.manager.submitStop(name);
                return envelope(ok ? 0 : 500, ok ? "已提交停止" : "停止失败",
                    this.manager.service(name).snapshot());
            }
            case "restart" -> {
                boolean ok = this.manager.submitRestart(name);
                return envelope(ok ? 0 : 500, ok ? "已提交重启" : "重启失败",
                    this.manager.service(name).snapshot());
            }
            default -> {
                return envelope(400, "未知动作：" + action, null);
            }
        }
    }

    private Map<String, Object> tail(String name) {
        Map<String, Object> data = new LinkedHashMap<>();
        ManagedService service = this.manager.service(name);
        if (service == null) {
            data.put("lines", List.of());
            return data;
        }
        Map<String, Object> snapshot = service.snapshot();
        Path log = Path.of(String.valueOf(snapshot.get("logFile")));
        List<String> lines = new ArrayList<>();
        try {
            if (Files.isRegularFile(log)) {
                List<String> all = Files.readAllLines(log, StandardCharsets.UTF_8);
                int from = Math.max(0, all.size() - this.maxLogLines);
                lines = new ArrayList<>(all.subList(from, all.size()));
            }
        } catch (Exception e) {
            lines.add("读取日志失败：" + e.getMessage());
        }
        data.put("name", name);
        data.put("file", log.toString());
        data.put("lines", lines);
        return data;
    }

    private boolean authorized(HttpExchange exchange) {
        String remote = exchange.getRemoteAddress().getAddress().getHostAddress();
        boolean loopback = InetAddress.getLoopbackAddress().getHostAddress().equals(remote)
            || "127.0.0.1".equals(remote) || "::1".equals(remote);
        if (this.token.isBlank()) {
            // 未配置令牌时只允许本机访问，避免控制面被局域网任意调用
            return loopback;
        }
        if (this.matchesToken(exchange.getRequestHeaders().getFirst("X-Watchdog-Token"))) {
            return true;
        }
        // 查询串按 key=value 逐项比对：早期实现用 contains("token=" + token) 子串匹配，
        // 配置了短令牌时 "token=1" 会命中 "token=123"，等于把控制面白送出去。
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return false;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0 || !"token".equals(pair.substring(0, eq))) {
                continue;
            }
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            if (this.matchesToken(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesToken(String candidate) {
        // 定长比较，避免按字符短路带来的时序侧信道
        return candidate != null
            && MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), this.token
                .getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> envelope(int code, String msg, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("success", code == 0);
        body.put("data", data);
        return body;
    }

    private void write(HttpExchange exchange, int status, Map<String, Object> body)
        throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
