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

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mbj.jdcompatiblemonitor.report.ReportBatch;
import com.mbj.jdcompatiblemonitor.report.ReportItem;
import com.mbj.jdcompatiblemonitor.report.ReportVariables;
import com.mbj.jdcompatiblemonitor.report.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import top.continew.admin.monitor.model.entity.BizReportDeadLetterDO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 死信重放（FR-RPT-05：死信可查看、可重放、可导出）。
 *
 * <p>MQTT / HTTP 目标直接重发原报文；数据库目标要求原报文为 JSON，
 * 以便还原成批次后按目标库模型写入（KV/自定义格式的数据库死信会明确提示不支持重放）。</p>
 *
 * @author jdrk
 * @since 2026/9/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportReplayService {

    private final ReportCatalog reportCatalog;
    private final ObjectProvider<ReportTransport> transportProvider;

    private final Map<String, ReportTransport> transports = new ConcurrentHashMap<>();

    /**
     * 重放一条死信
     *
     * @param entity 死信记录
     * @return 失败原因，成功返回 {@code null}
     */
    public String replay(BizReportDeadLetterDO entity) {
        this.ensureTransports();
        ReportTargetSpec target = reportCatalog.target(entity.getTargetId() == null ? -1L : entity
            .getTargetId());
        if (target == null) {
            return "原上报目标已不存在或已停用，无法重放";
        }
        ReportTransport transport = transports.get(target.targetType() == null ? "" : target
            .targetType()
            .toUpperCase());
        if (transport == null) {
            return "未接入的上报目标类型：" + target.targetType();
        }
        try {
            ReportTransmission transmission;
            if (target.database()) {
                ReportBatch batch = this.parseBatch(entity);
                if (batch == null) {
                    return "数据库死信重放要求报文格式为 JSON，当前格式：" + entity.getFormatType();
                }
                transmission = new ReportTransmission(target, null, batch, entity.getPayload());
            } else {
                transmission =
                    new ReportTransmission(target, this.topic(target, entity), null, entity
                        .getPayload());
            }
            transport.send(transmission);
            return null;
        } catch (Exception e) {
            log.warn("死信重放失败（{}）：{}", entity.getId(), e.getMessage());
            return e.getMessage();
        }
    }

    /**
     * 从死信报文还原批次
     *
     * @param entity 死信记录
     * @return 批次，无法还原返回 null
     */
    private ReportBatch parseBatch(BizReportDeadLetterDO entity) {
        String payload = entity.getPayload();
        if (payload == null || !JSONUtil.isTypeJSON(payload)) {
            return null;
        }
        try {
            JSONObject root = JSONUtil.parseObj(payload);
            JSONArray samples = root.getJSONArray("samples");
            List<ReportItem> items = new ArrayList<>();
            if (samples != null) {
                for (Object sample : samples) {
                    JSONObject json = (JSONObject) sample;
                    items
                        .add(new ReportItem(json.getInt("deviceAddr", 0), json.getStr("deviceName"),
                            json.getInt("nodeId", 0), json.getInt("analogNo", 0), json
                                .getStr("elementType"),
                            json.getStr("elementName"), json.getStr("unit"),
                            json.getDouble("value", 0.0D), json.getDouble("rawValue", 0.0D), json
                                .getLong("collectTimestamp", 0L),
                            json.getInt("alarm", 0) == 1, json
                                .getDouble("lng", null),
                            json.getDouble("lat", null)));
                }
            }
            return new ReportBatch(root.getLong("ruleId", 0L), root.getStr("ruleName"), root
                .getLong("targetId", 0L), root.getStr("targetName"), root.getStr("portName"),
                root
                    .getLong("batchTimestamp", System.currentTimeMillis()),
                items);
        } catch (Exception e) {
            log.warn("解析死信报文失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 还原 MQTT 主题
     *
     * @param target 目标
     * @param entity 死信记录
     * @return 主题
     */
    private String topic(ReportTargetSpec target, BizReportDeadLetterDO entity) {
        JSONObject config = JSONUtil.parseObj(target.configJson());
        String template = config.getStr("topic", "jdrk/data");
        ReportBatch batch = this.parseBatch(entity);
        if (batch == null || batch.items().isEmpty()) {
            return template;
        }
        Map<String, Object> variables = new HashMap<>(ReportVariables.ofBatch(batch));
        variables.putAll(ReportVariables.ofItem(batch.items().get(0)));
        return TemplateRenderer.render(template, variables);
    }

    private void ensureTransports() {
        if (!transports.isEmpty()) {
            return;
        }
        synchronized (this) {
            for (ReportTransport transport : transportProvider.orderedStream().toList()) {
                transports.put(transport.type().toUpperCase(), transport);
            }
        }
    }
}
