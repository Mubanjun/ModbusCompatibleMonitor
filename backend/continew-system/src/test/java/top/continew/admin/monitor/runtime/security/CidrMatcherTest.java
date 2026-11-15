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

package top.continew.admin.monitor.runtime.security;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端口来源白名单匹配单测（NFR-SEC-04，纯逻辑不依赖 Spring）
 *
 * @author jdrk
 * @since 2026/9/25
 */
class CidrMatcherTest {

    /** 与 SecurityProperties 默认值保持一致 */
    private static final List<CidrMatcher> DEFAULTS = CidrMatcher.parseAll(List.of("127.0.0.1/32",
        "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "fe80::/10",
        "fc00::/7"));

    @Test
    void loopbackAndPrivateRangeShouldPass() throws Exception {
        assertTrue(CidrMatcher.anyMatch(DEFAULTS, InetAddress.getByName("127.0.0.1")));
        assertTrue(CidrMatcher.anyMatch(DEFAULTS, InetAddress.getByName("::1")));
        assertTrue(CidrMatcher.anyMatch(DEFAULTS, InetAddress.getByName("10.1.2.3")));
        assertTrue(CidrMatcher.anyMatch(DEFAULTS, InetAddress.getByName("172.16.0.1")));
        assertTrue(CidrMatcher.anyMatch(DEFAULTS, InetAddress.getByName("192.168.74.128")));
    }

    @Test
    void publicAddressShouldBeRejected() throws Exception {
        assertFalse(CidrMatcher.anyMatch(DEFAULTS, InetAddress.getByName("8.8.8.8")));
        assertFalse(CidrMatcher.anyMatch(DEFAULTS, InetAddress.getByName("172.32.0.1")),
            "172.32 在 172.16/12 之外");
        assertFalse(CidrMatcher.anyMatch(DEFAULTS, InetAddress.getByName("192.169.0.1")),
            "192.169 不在 192.168/16");
        assertFalse(CidrMatcher.anyMatch(DEFAULTS, InetAddress.getByName("2001:db8::1")));
    }

    @Test
    void prefixBoundaryShouldBeExact() throws Exception {
        CidrMatcher matcher = CidrMatcher.parse("192.168.1.0/24");
        assertTrue(matcher.matches(InetAddress.getByName("192.168.1.0")));
        assertTrue(matcher.matches(InetAddress.getByName("192.168.1.255")));
        assertFalse(matcher.matches(InetAddress.getByName("192.168.2.0")));
        assertFalse(matcher.matches(InetAddress.getByName("192.168.0.255")));
    }

    @Test
    void singleAddressShouldBehaveAsFullPrefix() throws Exception {
        CidrMatcher matcher = CidrMatcher.parse("127.0.0.1");
        assertTrue(matcher.matches(InetAddress.getByName("127.0.0.1")));
        assertFalse(matcher.matches(InetAddress.getByName("127.0.0.2")));
    }

    @Test
    void zeroPrefixShouldMatchEverything() throws Exception {
        assertTrue(CidrMatcher.parse("0.0.0.0/0").matches(InetAddress.getByName("8.8.8.8")));
        assertTrue(CidrMatcher.parse("::/0").matches(InetAddress.getByName("2001:db8::1")));
    }

    @Test
    void ipv4MappedIpv6ShouldMatchIpv4Range() throws Exception {
        assertTrue(CidrMatcher.parse("192.168.0.0/16")
            .matches(InetAddress.getByName("::ffff:192.168.1.5")));
        assertFalse(CidrMatcher.parse("192.168.0.0/16")
            .matches(InetAddress.getByName("::ffff:8.8.8.8")));
    }

    @Test
    void ipv6LinkLocalAndUniqueLocalShouldBeScoped() throws Exception {
        assertTrue(CidrMatcher.parse("fe80::/10").matches(InetAddress.getByName("fe80::1")));
        assertFalse(CidrMatcher.parse("fe80::/10").matches(InetAddress.getByName("2001:db8::1")));
        assertTrue(CidrMatcher.parse("fc00::/7").matches(InetAddress.getByName("fd12::1")));
    }

    @Test
    void invalidConfigurationShouldFailFast() {
        assertThrows(IllegalArgumentException.class, () -> CidrMatcher.parse("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> CidrMatcher.parse("10.0.0.0/abc"));
        assertThrows(IllegalArgumentException.class, () -> CidrMatcher.parse("not-an-ip/8"));
        assertThrows(IllegalArgumentException.class, () -> CidrMatcher.parse(""));
        assertThrows(IllegalArgumentException.class, () -> CidrMatcher.parse("::1/129"));
    }
}
