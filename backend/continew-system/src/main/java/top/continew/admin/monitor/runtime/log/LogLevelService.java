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

package top.continew.admin.monitor.runtime.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import top.continew.admin.monitor.mapper.BizSysConfigMapper;
import top.continew.admin.monitor.model.entity.BizSysConfigDO;
import top.continew.admin.monitor.runtime.model.LogLevelResp;
import top.continew.starter.core.util.validation.CheckUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日志级别在线切换（FR-LOG-01：级别切换即时生效）。
 *
 * <p>只允许调整平台自身的采集/协议/报警/上报/同步/继电器等日志器，
 * 避免调用方随意修改框架日志级别影响排障；切换通过 Logback 上下文立即生效，
 * 不需要重启应用。</p>
 *
 * <p>切换结果同时持久化到业务配置 SYSTEM.LOG_LEVEL_OVERRIDES（形如
 * a=DEBUG;b=TRACE），应用启动时自动重新应用——现场「临时开 DEBUG 排障 → 重启」
 * 不会又退回默认级别，也不会因为忘记关而永久留下噪音（清空该配置即恢复 yml 默认）。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogLevelService {

    /** 持久化日志级别覆盖的配置项 code（SYSTEM 分类） */
    public static final String KEY_OVERRIDES = "LOG_LEVEL_OVERRIDES";

    /** 根日志器名称 */
    public static final String ROOT = "ROOT";

    /** 允许在线调整的日志器 */
    private static final List<String> MANAGED = List.of(ROOT,
        "top.continew.admin.monitor.runtime.engine",
        "top.continew.admin.monitor.runtime.session",
        "top.continew.admin.monitor.runtime.signal",
        "top.continew.admin.monitor.runtime.alarm",
        "top.continew.admin.monitor.runtime.report",
        "top.continew.admin.monitor.runtime.sync",
        "top.continew.admin.monitor.runtime.relay",
        "top.continew.admin.monitor.runtime.buffer",
        "com.mbj.jdcompatiblemonitor.protocol",
        "com.mbj.jdcompatiblemonitor.device");

    /** 可选级别 */
    private static final List<String> LEVELS =
        List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF");

    private final BizSysConfigMapper sysConfigMapper;

    /**
     * 列出受管理日志器的当前级别
     *
     * @return 级别列表
     */
    public List<LogLevelResp> list() {
        List<LogLevelResp> list = new ArrayList<>(MANAGED.size());
        for (String name : MANAGED) {
            list.add(this.toResp(name, true));
        }
        return list;
    }

    /**
     * 在线切换日志级别
     *
     * @param logger 日志器名称
     * @param level  目标级别
     * @return 切换后的级别
     */
    public LogLevelResp set(String logger, String level) {
        String name = logger == null ? "" : logger.trim();
        String target = level == null ? "" : level.trim().toUpperCase(Locale.ROOT);
        CheckUtils.throwIf(!MANAGED.contains(name), "不允许调整日志器 [%s] 的级别", name);
        CheckUtils.throwIf(!LEVELS.contains(target), "不支持的日志级别 [%s]", target);
        Logger instance = this.logger(name);
        instance.setLevel(Level.toLevel(target));
        this.persist(name, target);
        log.info("日志级别已切换：{} -> {}", name, target);
        return this.toResp(name, true);
    }

    /**
     * 启动时重新应用持久化的日志级别（FR-LOG-01）
     *
     * <p>放在 {@link ApplicationReadyEvent}：此时 Logback 已完成初始化，
     * 覆盖不会在后续的配置刷新中被冲掉。</p>
     *
     * @param event 就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void applyPersisted(ApplicationReadyEvent event) {
        List<String> pairs = this.storedPairs();
        if (pairs.isEmpty()) {
            return;
        }
        int applied = 0;
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            String name = parts[0].trim();
            String level = parts[1].trim().toUpperCase(Locale.ROOT);
            if (!MANAGED.contains(name) || !LEVELS.contains(level)) {
                continue;
            }
            this.logger(name).setLevel(Level.toLevel(level));
            applied++;
        }
        if (applied > 0) {
            log.info("已应用持久化的日志级别 {} 项：{}", applied, String.join("；", pairs));
        }
    }

    /**
     * 持久化一次级别覆盖（先合并已有项，再整体写回）
     *
     * @param logger 日志器名称
     * @param level  级别
     */
    private void persist(String logger, String level) {
        Map<String, String> overrides = new LinkedHashMap<>();
        for (String pair : this.storedPairs()) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                overrides.put(parts[0].trim(), parts[1].trim());
            }
        }
        overrides.put(logger, level);
        String value = overrides.entrySet().stream().map(entry -> entry.getKey() + "=" + entry
            .getValue()).collect(Collectors.joining(";"));
        BizSysConfigDO existing = sysConfigMapper.lambdaQuery()
            .eq(BizSysConfigDO::getCategory, "SYSTEM")
            .eq(BizSysConfigDO::getCode, KEY_OVERRIDES)
            .last("LIMIT 1")
            .one();
        if (existing == null) {
            BizSysConfigDO entity = new BizSysConfigDO();
            entity.setCategory("SYSTEM");
            entity.setName("日志级别覆盖");
            entity.setCode(KEY_OVERRIDES);
            entity.setValue(value);
            entity.setDefaultValue("");
            entity.setDescription("在线切换过的日志级别，重启后自动应用（FR-LOG-01）；清空该配置即恢复 yml 默认");
            sysConfigMapper.insert(entity);
        } else {
            BizSysConfigDO update = new BizSysConfigDO();
            update.setId(existing.getId());
            update.setValue(value);
            sysConfigMapper.updateById(update);
        }
    }

    /**
     * 读取持久化的覆盖项
     *
     * @return 形如 {@code logger=LEVEL} 的列表（无配置时为空）
     */
    private List<String> storedPairs() {
        BizSysConfigDO row = sysConfigMapper.lambdaQuery()
            .eq(BizSysConfigDO::getCategory, "SYSTEM")
            .eq(BizSysConfigDO::getCode, KEY_OVERRIDES)
            .last("LIMIT 1")
            .one();
        String value = row == null ? null : row.getValue();
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> pairs = new ArrayList<>();
        for (String pair : value.split(";")) {
            if (!pair.isBlank()) {
                pairs.add(pair.trim());
            }
        }
        return pairs;
    }

    /**
     * 查询指定日志器的当前级别
     *
     * @param logger 日志器名称
     * @return 级别
     */
    public LogLevelResp get(String logger) {
        String name = logger == null ? "" : logger.trim();
        return this.toResp(name, MANAGED.contains(name));
    }

    private LogLevelResp toResp(String name, boolean managed) {
        LogLevelResp resp = new LogLevelResp();
        resp.setLogger(name);
        resp.setManaged(managed);
        Level level = this.logger(name).getEffectiveLevel();
        resp.setLevel(level == null ? null : level.toString());
        return resp;
    }

    private Logger logger(String name) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return ROOT.equalsIgnoreCase(name) ? context.getLogger(Logger.ROOT_LOGGER_NAME) : context
            .getLogger(name);
    }
}
