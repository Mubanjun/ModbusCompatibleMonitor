/*
 * Copyright (c) 2026-present 仁科兼容环境监测平台 Authors. All Rights Reserved.
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

package com.mbj.jdcompatiblemonitor.protocol;

/**
 * 一次收发报文的留痕，用于信令监视（FR-SIG）。
 *
 * @param timeMillis 时间戳（毫秒）
 * @param outbound   是否为发送方向
 * @param slaveAddress 从站地址（未知时为 -1）
 * @param frame      报文字节
 * @param note       附加说明（如超时、校验失败原因）
 */
public record FrameTrace(long timeMillis, boolean outbound, int slaveAddress, byte[] frame,
    String note) {

    /**
     * 构造发送留痕
     *
     * @param slaveAddress 从站地址
     * @param frame        报文
     * @return 留痕
     */
    public static FrameTrace out(int slaveAddress, byte[] frame) {
        return new FrameTrace(System.currentTimeMillis(), true, slaveAddress, frame, null);
    }

    /**
     * 构造接收留痕
     *
     * @param slaveAddress 从站地址
     * @param frame        报文
     * @return 留痕
     */
    public static FrameTrace in(int slaveAddress, byte[] frame) {
        return new FrameTrace(System.currentTimeMillis(), false, slaveAddress, frame, null);
    }

    /**
     * 构造异常留痕
     *
     * @param slaveAddress 从站地址
     * @param note         原因
     * @return 留痕
     */
    public static FrameTrace error(int slaveAddress, String note) {
        return new FrameTrace(System.currentTimeMillis(), false, slaveAddress, new byte[0], note);
    }
}
