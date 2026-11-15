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

package top.continew.admin.monitor.runtime.buffer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.runtime.service.RuntimeSettingsService;

import java.io.File;

/**
 * 磁盘剩余空间保护（NFR-FAULT-03）：监控暂存库所在分区的剩余空间，
 * 低于告警阈值时给自检项 FAIL（心跳健康位随之变化，桌面壳会通知），
 * 低于保护阈值时通知暂存服务进入保护模式（收紧本地暂存上限并立即尝试落地）。
 *
 * <p>阈值可用 {@code STORAGE_MIN_FREE_MB} / {@code STORAGE_PROTECT_FREE_MB} 在业务系统配置中调整。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiskSpaceGuard {

    /** 一兆字节 */
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final StagingProperties properties;
    private final RuntimeSettingsService runtimeSettingsService;

    /**
     * 磁盘空间级别
     */
    public enum Level {
        /** 正常 */
        OK,
        /** 低于告警阈值 */
        WARN,
        /** 低于保护阈值 */
        PROTECT
    }

    /**
     * 判定级别（纯函数，便于单测）
     *
     * @param freeMb     剩余空间（MB）
     * @param minFreeMb  告警阈值（MB）
     * @param protectFreeMb 保护阈值（MB）
     * @return 级别
     */
    public static Level decide(long freeMb, long minFreeMb, long protectFreeMb) {
        if (freeMb <= protectFreeMb) {
            return Level.PROTECT;
        }
        if (freeMb <= minFreeMb) {
            return Level.WARN;
        }
        return Level.OK;
    }

    /**
     * 暂存库所在分区的剩余空间（MB）；取不到时返回 {@link Long#MAX_VALUE}（按正常处理，避免误告警）
     *
     * @return 剩余空间（MB）
     */
    public long freeMb() {
        File file = new File(properties.getDatabasePath());
        File dir = file.getAbsoluteFile().getParentFile();
        while (dir != null && !dir.exists()) {
            dir = dir.getParentFile();
        }
        long usable = dir == null ? file.getAbsoluteFile().getUsableSpace() : dir.getUsableSpace();
        if (usable <= 0L) {
            log.debug("无法获取磁盘剩余空间，按正常处理（path={}）", properties.getDatabasePath());
            return Long.MAX_VALUE;
        }
        return usable / BYTES_PER_MB;
    }

    /**
     * 告警阈值（MB）
     *
     * @return 生效阈值
     */
    public long minFreeMb() {
        return runtimeSettingsService.minFreeMb(properties.getMinFreeMb());
    }

    /**
     * 保护阈值（MB）
     *
     * @return 生效阈值
     */
    public long protectFreeMb() {
        return runtimeSettingsService.protectFreeMb(properties.getProtectFreeMb());
    }

    /**
     * 当前级别
     *
     * @return 级别
     */
    public Level level() {
        return decide(this.freeMb(), this.minFreeMb(), this.protectFreeMb());
    }

    /**
     * 是否处于保护级别
     *
     * @return true 表示低于保护阈值
     */
    public boolean inProtection() {
        return this.level() == Level.PROTECT;
    }

    /**
     * 描述文案（自检项与状态接口共用）
     *
     * @return 描述
     */
    public String describe() {
        return "剩余 " + this.freeMb() + "MB（告警 <" + this.minFreeMb() + "MB，保护 <"
            + this.protectFreeMb() + "MB）";
    }
}
