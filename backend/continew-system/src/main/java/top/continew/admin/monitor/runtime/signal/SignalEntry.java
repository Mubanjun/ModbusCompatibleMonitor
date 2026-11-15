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

package top.continew.admin.monitor.runtime.signal;

/**
 * 实时信令流的一条记录（FR-SIG-01）
 *
 * @param sequence   单调序号，供前端增量拉取
 * @param timeMillis 报文时间（毫秒时间戳）
 * @param portId     端口 ID
 * @param portName   端口地址
 * @param deviceId   设备 ID
 * @param deviceName 设备名称
 * @param deviceAddr 从站地址
 * @param direction  方向（OUT / IN / ERROR）
 * @param frame      十六进制报文
 * @param frameLength 报文字节数
 * @param parsed     解析结果
 * @param durationMs 本次轮询耗时（毫秒）
 * @param success    是否成功
 * @param errorMsg   失败原因
 * @author jdrk
 * @since 2026/9/23
 */
public record SignalEntry(long sequence, long timeMillis, Long portId, String portName,
    Long deviceId,
    String deviceName, Integer deviceAddr, String direction, String frame, int frameLength,
    String parsed, int durationMs, boolean success, String errorMsg) {
}
