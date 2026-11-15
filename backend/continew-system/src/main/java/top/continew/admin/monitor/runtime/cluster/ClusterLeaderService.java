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

package top.continew.admin.monitor.runtime.cluster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 集群选主与租约续期（NFR-SCALE-04）
 *
 * <p>用 Redis 的 SET NX EX 抢租约，续租与释放都用 Lua 脚本「比较令牌后再操作」，
 * 避免租约超时被别人接管后，旧主节点又把别人的锁续上或删掉。默认（jdrk.cluster.enabled=false）
 * 不连 Redis，本实例直接按主节点运行，行为与单机版本完全一致。</p>
 *
 * <p>选主失败或 Redis 异常时一律降为从节点：宁可少采，也不能两个实例同时写业务库。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterLeaderService {

    /** 续租脚本：令牌一致才延长租约，否则返回 0（说明锁已不属于本实例） */
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('expire', KEYS[1], ARGV[2]) "
            + "else return 0 end",
        Long.class);

    /** 释放脚本：令牌一致才删除 */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);

    private final ClusterProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ApplicationEventPublisher eventPublisher;

    /** 本实例当前是否持有租约 */
    private final AtomicBoolean leader = new AtomicBoolean(false);

    /** 本次持有的租约令牌（每次启动都不同） */
    private final String token = UUID.randomUUID().toString();

    private volatile String instanceId;
    private volatile String lastError;
    private volatile ScheduledExecutorService scheduler;

    /**
     * 启动时先抢一次租约；此时只确定角色，不发布事件（各引擎还没到 ApplicationReady）
     */
    @PostConstruct
    public void init() {
        this.instanceId = this.resolveInstanceId();
        if (!properties.isEnabled()) {
            log.info("集群模式未启用（jdrk.cluster.enabled=false）：本实例按单机主节点运行，实例号 {}", instanceId);
            return;
        }
        this.tryAcquire();
        int interval = Math.max(1, Math.min(properties.getRenewSeconds(), Math.max(1, properties
            .getLeaseSeconds() - 1)));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "jdrk-cluster-elector");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler.scheduleWithFixedDelay(this::elect, interval, interval, TimeUnit.SECONDS);
        log.info("集群模式已启用：租约键 {}，租约 {} 秒，续租 {} 秒，实例 {}", properties.getLockKey(),
            properties.getLeaseSeconds(), interval, instanceId);
    }

    /**
     * 退出时释放租约，让其他实例尽快接管
     */
    @PreDestroy
    public void release() {
        ScheduledExecutorService executor = scheduler;
        scheduler = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        if (!properties.isEnabled()) {
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null || !leader.get()) {
            return;
        }
        try {
            redis.execute(RELEASE_SCRIPT, List.of(properties.getLockKey()), token);
        } catch (Exception e) {
            log.debug("释放集群租约失败（等租约到期自动释放）：{}", e.getMessage());
        }
    }

    /**
     * 当前是否承担采集职责（单机模式下恒为 true）
     *
     * @return 是否主节点
     */
    public boolean isLeader() {
        return !properties.isEnabled() || leader.get();
    }

    /**
     * 当前角色名
     *
     * @return SINGLE（未启用集群）/ LEADER / FOLLOWER
     */
    public String role() {
        if (!properties.isEnabled()) {
            return "SINGLE";
        }
        return leader.get() ? "LEADER" : "FOLLOWER";
    }

    /**
     * 实例标识（用于日志与运维界面区分实例）
     *
     * @return 实例标识
     */
    public String instanceId() {
        return instanceId;
    }

    /**
     * 最近一次选主异常（用于运维界面排查）
     *
     * @return 异常信息，正常时为 null
     */
    public String lastError() {
        return lastError;
    }

    /** 仅确定初始角色，不发布事件（避免在 ApplicationReady 之前启动引擎） */
    private void tryAcquire() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            lastError = "未配置 Redis 连接";
            log.warn("集群模式已启用但拿不到 Redis 连接，本实例以从节点运行（不采集、不写入）");
            return;
        }
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(properties.getLockKey(), token,
                Duration.ofSeconds(Math.max(properties.getLeaseSeconds(), 5)));
            if (Boolean.TRUE.equals(acquired)) {
                leader.set(true);
                lastError = null;
            } else {
                log.info("租约键已被占用，本实例以从节点运行（只提供查询与配置）");
            }
        } catch (Exception e) {
            lastError = e.getMessage();
            log.warn("抢占集群租约失败，本实例以从节点运行：{}", e.getMessage());
        }
    }

    /** 周期性选主/续租，角色变化时发布事件 */
    private void elect() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            this.demote("拿不到 Redis 连接");
            return;
        }
        int lease = Math.max(properties.getLeaseSeconds(), 5);
        try {
            if (leader.get()) {
                Long renewed = redis.execute(RENEW_SCRIPT, List.of(properties.getLockKey()), token,
                    String.valueOf(lease));
                if (renewed == null || renewed == 0L) {
                    this.demote("租约已失效（可能已被其他实例接管）");
                }
            } else {
                Boolean acquired = redis.opsForValue().setIfAbsent(properties.getLockKey(), token,
                    Duration.ofSeconds(lease));
                if (Boolean.TRUE.equals(acquired)) {
                    this.promote("抢到租约");
                }
            }
            lastError = null;
        } catch (Exception e) {
            lastError = e.getMessage();
            if (leader.get()) {
                this.demote("Redis 异常：" + e.getMessage());
            } else {
                log.warn("集群选主失败（保持从节点）：{}", e.getMessage());
            }
        }
    }

    /** 升为主节点 */
    private void promote(String reason) {
        if (leader.compareAndSet(false, true)) {
            log.info("已成为集群主节点（{}）：采集/上报/暂存由本实例承担（实例 {}）", reason, instanceId);
            eventPublisher.publishEvent(new ClusterRoleChangedEvent(true, reason, instanceId));
        }
    }

    /** 降为从节点 */
    private void demote(String reason) {
        if (leader.compareAndSet(true, false)) {
            log.warn("已降为集群从节点（{}）：停止采集/上报/暂存，仅提供查询与配置（实例 {}）", reason, instanceId);
            eventPublisher.publishEvent(new ClusterRoleChangedEvent(false, reason, instanceId));
        }
    }

    /** 解析实例标识：配置优先，否则「主机名-pid」 */
    private String resolveInstanceId() {
        String configured = properties.getInstanceId();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }
        return host + "-" + ProcessHandle.current().pid();
    }
}
