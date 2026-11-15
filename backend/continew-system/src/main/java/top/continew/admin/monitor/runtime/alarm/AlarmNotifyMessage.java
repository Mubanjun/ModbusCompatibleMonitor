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

package top.continew.admin.monitor.runtime.alarm;

import java.time.LocalDateTime;

/**
 * 一条待发送的报警通知（FR-ALM-04）。
 *
 * @param recordId     报警记录 ID
 * @param ruleId       规则 ID
 * @param ruleName     规则名称
 * @param notifyType   通知方式（MQTT / EMAIL / SMS）
 * @param target       通知目标（主题 / 邮箱 / 手机号）
 * @param deviceName   设备名称
 * @param deviceAddr   设备地址
 * @param nodeId       节点编号
 * @param analogNo     模拟量序号
 * @param elementName  要素名称
 * @param alarmType    报警类型
 * @param alarmMessage 报警信息
 * @param alarmRange   报警范围
 * @param dataValue    报警数值
 * @param unit         单位
 * @param recordTime   记录时间
 * @param recovered    是否为恢复通知
 */
public record AlarmNotifyMessage(Long recordId, long ruleId, String ruleName, String notifyType,
    String target, String deviceName, Integer deviceAddr, Integer nodeId, Integer analogNo,
    String elementName, String alarmType, String alarmMessage, String alarmRange, Double dataValue,
    String unit, LocalDateTime recordTime, boolean recovered) {

    /**
     * 生成通知正文
     *
     * @return 正文
     */
    public String content() {
        return "[仁科兼容环境监测平台]" + (recovered ? "报警恢复" : "报警") + "\n"
            + "规则：" + ruleName + "\n"
            + "设备：" + deviceName + "（地址 " + deviceAddr + "）\n"
            + "节点：" + nodeId + " / 模拟量 " + analogNo + "\n"
            + "要素：" + elementName + "\n"
            + "类型：" + alarmType + "\n"
            + "信息：" + alarmMessage + "\n"
            + "范围：" + alarmRange + "\n"
            + "数值：" + dataValue + " " + (unit == null ? "" : unit) + "\n"
            + "时间：" + recordTime;
    }

    /**
     * 生成通知标题
     *
     * @return 标题
     */
    public String subject() {
        return "[环境监测]" + (recovered ? "恢复" : "报警") + "·" + elementName + "·" + deviceName;
    }
}
