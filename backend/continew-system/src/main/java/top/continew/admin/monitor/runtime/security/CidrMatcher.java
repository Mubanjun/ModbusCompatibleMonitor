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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * CIDR 网段匹配（NFR-SEC-04：管理端口仅回环或内网白名单）
 *
 * <p>纯逻辑、无 Spring 依赖：按位比较网络前缀，支持 IPv4 与 IPv6，也接受不带前缀的单个地址
 * （视为 /32 或 /128）。配置写错时必须立刻抛错——白名单配置错误若被静默忽略，等于没有白名单。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
public final class CidrMatcher {

    /** 网络地址（主机位已归零） */
    private final byte[] network;

    /** 前缀长度（比特） */
    private final int prefixBits;

    /** 原始写法，便于报错与日志展示 */
    private final String source;

    private CidrMatcher(byte[] network, int prefixBits, String source) {
        this.network = network;
        this.prefixBits = prefixBits;
        this.source = source;
    }

    /**
     * 解析 CIDR 或单个 IP 地址
     *
     * @param cidr 例如 192.168.0.0/16、127.0.0.1、::1/128
     * @return 匹配器
     * @throws IllegalArgumentException 写法非法或前缀长度超出地址位数
     */
    public static CidrMatcher parse(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            throw new IllegalArgumentException("CIDR 不能为空");
        }
        String text = cidr.trim();
        String addressPart = text;
        int bits = -1;
        int slash = text.indexOf('/');
        if (slash >= 0) {
            addressPart = text.substring(0, slash).trim();
            try {
                bits = Integer.parseInt(text.substring(slash + 1).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("CIDR 前缀长度不合法：" + text);
            }
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(addressPart);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("CIDR 地址不合法：" + text);
        }
        byte[] raw = address.getAddress();
        int totalBits = raw.length * 8;
        if (bits < 0) {
            bits = totalBits;
        }
        if (bits > totalBits) {
            throw new IllegalArgumentException("CIDR 前缀长度超出地址位数：" + text);
        }
        byte[] network = new byte[raw.length];
        for (int i = 0; i < raw.length; i++) {
            int remain = bits - i * 8;
            if (remain >= 8) {
                network[i] = raw[i];
            } else if (remain > 0) {
                network[i] = (byte) (raw[i] & (0xFF << (8 - remain)));
            }
        }
        return new CidrMatcher(network, bits, text);
    }

    /**
     * 批量解析（配置错误在启动阶段暴露）
     *
     * @param cidrs CIDR 列表
     * @return 匹配器列表
     */
    public static List<CidrMatcher> parseAll(List<String> cidrs) {
        List<CidrMatcher> matchers = new ArrayList<>();
        for (String cidr : cidrs == null ? List.<String>of() : cidrs) {
            if (cidr == null || cidr.isBlank()) {
                continue;
            }
            matchers.add(parse(cidr));
        }
        return matchers;
    }

    /**
     * 判断地址是否落在本网段内
     *
     * @param address 待判定地址
     * @return true 表示命中
     */
    public boolean matches(InetAddress address) {
        if (address == null) {
            return false;
        }
        byte[] candidate = normalize(address);
        if (candidate.length != network.length) {
            return false;
        }
        for (int i = 0; i * 8 < prefixBits; i++) {
            int remain = prefixBits - i * 8;
            int mask = remain >= 8 ? 0xFF : (0xFF << (8 - remain)) & 0xFF;
            if ((candidate[i] & mask) != (network[i] & mask)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 任意一个网段命中即放行
     *
     * @param matchers 匹配器列表
     * @param address 待判定地址
     * @return true 表示命中
     */
    public static boolean anyMatch(List<CidrMatcher> matchers, InetAddress address) {
        for (CidrMatcher matcher : matchers) {
            if (matcher.matches(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把 IPv4-mapped IPv6（::ffff:192.168.1.5）规整为 IPv4，
     * 避免同一客户端因网卡栈不同（IPv4/IPv6 双栈）而出现「有时放行、有时拒绝」
     */
    private static byte[] normalize(InetAddress address) {
        byte[] raw = address.getAddress();
        if (address instanceof Inet6Address && raw.length == 16) {
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (raw[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && raw[10] == (byte) 0xFF && raw[11] == (byte) 0xFF) {
                byte[] ipv4 = new byte[4];
                System.arraycopy(raw, 12, ipv4, 0, 4);
                return ipv4;
            }
        }
        return raw;
    }

    @Override
    public String toString() {
        return source;
    }
}
