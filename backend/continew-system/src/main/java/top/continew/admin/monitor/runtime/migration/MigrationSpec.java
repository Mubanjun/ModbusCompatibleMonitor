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

package top.continew.admin.monitor.runtime.migration;

/**
 * 源库（仁科）连接参数
 *
 * @param host     主机
 * @param port     端口
 * @param database 库名
 * @param username 用户名
 * @param password 口令
 * @author jdrk
 * @since 2026/9/24
 */
public record MigrationSpec(String host, Integer port, String database, String username,
    String password) {

    /**
     * 构造 JDBC URL（MySQL）
     *
     * @param connectTimeoutSeconds 连接超时（秒）
     * @return URL
     */
    public String jdbcUrl(int connectTimeoutSeconds) {
        // 缺陷 72（安全，纵深防御）：不信任上层是否已校验，这里再挡一次 URL 元字符，
        // 避免 host/database 里的 `?`/`&`/`=`/`#`/`/` 把连接属性注入进 JDBC URL。
        String safeHost = this.safe(host, "^[A-Za-z0-9._-]{1,128}$", "源库主机");
        String safeDatabase = this.safe(database, "^[A-Za-z0-9_$-]{1,64}$", "源库名");
        return "jdbc:mysql://" + safeHost + ":" + (port == null ? 3306 : port) + "/" + safeDatabase
            + "?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout="
            + Math.max(1, connectTimeoutSeconds) * 1000;
    }

    /**
     * 校验并返回合法取值（不合法直接拒绝，不做静默替换）
     *
     * @param value 待校验值
     * @param regex 允许的字符集
     * @param label 字段名（用于错误信息）
     * @return 原值
     */
    private String safe(String value, String regex, String label) {
        if (value == null || !value.matches(regex)) {
            throw new IllegalArgumentException(label + "不合法：" + value);
        }
        return value;
    }
}
