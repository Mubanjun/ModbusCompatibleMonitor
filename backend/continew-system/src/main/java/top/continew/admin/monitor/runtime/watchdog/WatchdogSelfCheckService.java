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

package top.continew.admin.monitor.runtime.watchdog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizReportDeadLetterMapper;
import top.continew.admin.monitor.mapper.BizSyncTaskMapper;
import top.continew.admin.monitor.runtime.buffer.DiskSpaceGuard;
import top.continew.admin.monitor.runtime.buffer.StagingBufferService;
import top.continew.admin.monitor.runtime.cluster.ClusterLeaderService;
import top.continew.admin.monitor.runtime.cluster.ClusterProperties;
import top.continew.admin.monitor.runtime.engine.AcquisitionEngine;
import top.continew.admin.monitor.runtime.model.AcquisitionStatusResp;
import top.continew.admin.monitor.runtime.model.SelfCheckItemResp;
import top.continew.admin.monitor.runtime.model.StagingStatusResp;
import top.continew.starter.cache.redisson.util.RedisUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 看门狗自检项（FR-SYS-03、FR-WD-06、NFR-FAULT-03）
 *
 * <p>轻量检查（数据库/采集调度/本地暂存/Redis/磁盘空间）用于心跳，
 * 完整自检在此基础上补暂存明细、端口与设备、上报死信、同步任务、心跳与日志目录可写。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchdogSelfCheckService {

    private final JdbcTemplate jdbcTemplate;
    private final AcquisitionEngine acquisitionEngine;
    private final StagingBufferService stagingBufferService;
    private final DiskSpaceGuard diskSpaceGuard;
    private final BizReportDeadLetterMapper reportDeadLetterMapper;
    private final BizSyncTaskMapper syncTaskMapper;
    private final ClusterProperties clusterProperties;
    private final ClusterLeaderService clusterLeaderService;

    /**
     * 轻量检查（心跳用）
     *
     * @return 检查项
     */
    public List<SelfCheckItemResp> lightChecks() {
        List<SelfCheckItemResp> items = new ArrayList<>(4);
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            items.add(SelfCheckItemResp.pass("数据库", "连接正常"));
        } catch (Exception e) {
            items.add(SelfCheckItemResp.fail("数据库", e.getMessage()));
        }
        AcquisitionStatusResp snapshot;
        try {
            snapshot = acquisitionEngine.snapshot();
            if (clusterProperties.isEnabled() && !clusterLeaderService.isLeader()) {
                // 集群从节点本来就不采集，报 FAIL 会让整个集群的从节点一直是「不健康」
                items.add(SelfCheckItemResp.pass("采集调度", "集群从节点：不承担采集（角色 "
                    + clusterLeaderService.role() + "，由主节点采集）"));
            } else if (acquisitionEngine.isRunning()) {
                items.add(SelfCheckItemResp.pass("采集调度", "运行中，轮次 " + snapshot.getRoundCount()
                    + "，采样 " + snapshot.getSampleCount()));
            } else {
                items.add(SelfCheckItemResp.fail("采集调度", "调度未运行"));
            }
        } catch (Exception e) {
            items.add(SelfCheckItemResp.fail("采集调度", e.getMessage()));
        }
        try {
            StagingStatusResp staging = stagingBufferService.status();
            boolean ok = staging.getLastError() == null;
            items.add(new SelfCheckItemResp("本地暂存", ok, "待上传 " + staging.getPendingCount()
                + "，积压 " + staging.getBacklog() + (ok ? "" : "，最近错误：" + staging.getLastError())));
        } catch (Exception e) {
            items.add(SelfCheckItemResp.fail("本地暂存", e.getMessage()));
        }
        items.add(this.checkRedis());
        items.add(this.checkDiskSpace());
        return items;
    }

    /**
     * 完整自检（FR-WD-06）
     *
     * @param heartbeatDir 心跳目录
     * @param logDir       看门狗日志目录
     * @return 检查项
     */
    public List<SelfCheckItemResp> selfCheck(Path heartbeatDir, Path logDir) {
        List<SelfCheckItemResp> items = new ArrayList<>(this.lightChecks());
        items.add(this.checkStagingDetail());
        items.add(this.checkPorts());
        items.add(this.checkReport());
        items.add(this.checkSync());
        items.add(this.checkWritable(heartbeatDir, "心跳目录"));
        items.add(this.checkWritable(logDir, "看门狗日志目录"));
        return items;
    }

    /**
     * 磁盘空间检查（NFR-FAULT-03）
     *
     * <p>低于告警阈值即 FAIL（心跳健康位随之变化，桌面壳与看门狗都会提示）；
     * 低于保护阈值时额外通知暂存服务进入保护模式（收紧本地暂存上限 + 立即尝试落地）。</p>
     *
     * @return 检查项
     */
    private SelfCheckItemResp checkDiskSpace() {
        try {
            long freeMb = diskSpaceGuard.freeMb();
            DiskSpaceGuard.Level level = diskSpaceGuard.level();
            if (level == DiskSpaceGuard.Level.PROTECT) {
                String action = stagingBufferService.relieveDiskPressure();
                return SelfCheckItemResp.fail("磁盘空间", "剩余 " + freeMb + "MB 低于保护阈值 "
                    + diskSpaceGuard.protectFreeMb() + "MB，" + action);
            }
            if (level == DiskSpaceGuard.Level.WARN) {
                return SelfCheckItemResp.fail("磁盘空间", "剩余 " + freeMb + "MB 低于告警阈值 "
                    + diskSpaceGuard.minFreeMb() + "MB，请清理磁盘");
            }
            stagingBufferService.exitProtection();
            return SelfCheckItemResp.pass("磁盘空间", diskSpaceGuard.describe());
        } catch (Exception e) {
            return SelfCheckItemResp.fail("磁盘空间", e.getMessage());
        }
    }

    /**
     * Redis 依赖检查（FR-SYS-03）
     *
     * <p>会话、票据、缓存都在 Redis 上，缺了它「页面能打开但登录不上/票据失效」，
     * 因此和数据库一样列为必检项；用一次写-读-删验证可读写而不是只 ping 连通性。</p>
     *
     * @return 检查项
     */
    private SelfCheckItemResp checkRedis() {
        String key = "jdrk:selfcheck:ping";
        try {
            RedisUtils.set(key, "ok", Duration.ofSeconds(10));
            String value = RedisUtils.get(key);
            RedisUtils.delete(key);
            boolean ok = "ok".equals(value);
            return ok ? SelfCheckItemResp.pass("Redis", "读写正常") : SelfCheckItemResp.fail("Redis",
                "写入后读回不一致：" + value);
        } catch (Exception e) {
            return SelfCheckItemResp.fail("Redis", e.getMessage());
        }
    }

    private SelfCheckItemResp checkStagingDetail() {
        try {
            StagingStatusResp staging = stagingBufferService.status();
            String detail = "文件 " + staging.getFileSizeBytes() + " 字节，库 " + staging
                .getDatabasePath() + "，失败批次 " + staging.getFailedBatches();
            return SelfCheckItemResp.pass("暂存明细", detail);
        } catch (Exception e) {
            return SelfCheckItemResp.fail("暂存明细", e.getMessage());
        }
    }

    private SelfCheckItemResp checkPorts() {
        try {
            AcquisitionStatusResp snapshot = acquisitionEngine.snapshot();
            int ports = snapshot.getPorts() == null ? 0 : snapshot.getPorts().size();
            if (ports == 0) {
                return SelfCheckItemResp.pass("端口与设备", "未配置端口");
            }
            return SelfCheckItemResp.pass("端口与设备", ports + " 个端口已加载"
                + (snapshot.getPaused() != null && snapshot.getPaused() ? "（已暂停）" : ""));
        } catch (Exception e) {
            return SelfCheckItemResp.fail("端口与设备", e.getMessage());
        }
    }

    private SelfCheckItemResp checkReport() {
        try {
            Long pending = reportDeadLetterMapper.selectCount(null);
            long count = pending == null ? 0L : pending;
            if (count > 1000L) {
                return SelfCheckItemResp.fail("上报死信", "死信积压 " + count + " 条，请检查上报通道");
            }
            return SelfCheckItemResp.pass("上报死信", "累计 " + count + " 条");
        } catch (Exception e) {
            return SelfCheckItemResp.fail("上报死信", e.getMessage());
        }
    }

    private SelfCheckItemResp checkSync() {
        try {
            Long tasks = syncTaskMapper.selectCount(null);
            return SelfCheckItemResp.pass("同步任务", "已配置 " + (tasks == null ? 0L : tasks) + " 个");
        } catch (Exception e) {
            return SelfCheckItemResp.fail("同步任务", e.getMessage());
        }
    }

    /**
     * 检查目录可写。
     *
     * <p>用临时文件探测，不能直接往心跳/日志文件里写字：自检曾在心跳文件里写下「-」，
     * 把刚写好的心跳内容覆盖成无效 JSON。</p>
     *
     * @param dir  目录
     * @param name 检查项名称
     * @return 检查项
     */
    private SelfCheckItemResp checkWritable(Path dir, String name) {
        Path probe = null;
        try {
            Path target = dir.toAbsolutePath().normalize();
            Files.createDirectories(target);
            probe = Files.createTempFile(target, ".selfcheck-", ".tmp");
            return SelfCheckItemResp.pass(name, target.toString());
        } catch (Exception e) {
            return SelfCheckItemResp.fail(name, e.getMessage());
        } finally {
            this.deleteQuietly(probe);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("删除自检临时文件失败：{}", e.getMessage());
        }
    }
}
