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

import java.time.DateTimeException;
import java.time.LocalDateTime;

/**
 * 标准 ModBus 规约的时间寄存器编解码（需求规格说明书 附录 A）。
 *
 * <p>寄存器 684~689 依次为 年、月、日、时、分、秒，均为 16 位无符号、可读可写，
 * 平台下发使用功能码 16（写多个寄存器），读取使用功能码 03。示例：校时为
 * 2017-03-28 09:59:32 时，待写入的 6 个寄存器为
 * {@code 07 E1 00 03 00 1C 00 09 00 3B 00 20}。</p>
 *
 * <p>仁科 RS-Modbus（附录 B）寄存器语义不同且只读，不适用本编解码。</p>
 */
public final class ModbusTimeCodec {

    /** 时间寄存器起始地址（年）。 */
    public static final int REGISTER_BASE = 684;

    /** 时间寄存器个数（年、月、日、时、分、秒）。 */
    public static final int REGISTER_COUNT = 6;

    /** 年寄存器允许的最小值（附录 A：2000 至 3000）。 */
    public static final int MIN_YEAR = 2000;

    /** 年寄存器允许的最大值。 */
    public static final int MAX_YEAR = 3000;

    private ModbusTimeCodec() {
    }

    /**
     * 把时间编码为待写入的 6 个寄存器值。
     *
     * @param time 时间
     * @return 长度为 {@link #REGISTER_COUNT} 的寄存器值，依次为年、月、日、时、分、秒
     */
    public static int[] encode(LocalDateTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time must not be null");
        }
        int year = time.getYear();
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException("year out of range: " + year);
        }
        return new int[] {
            year,
            time.getMonthValue(),
            time.getDayOfMonth(),
            time.getHour(),
            time.getMinute(),
            time.getSecond()
        };
    }

    /**
     * 解析从设备读回的时间寄存器。
     *
     * @param registers 寄存器值，前 6 项依次为年、月、日、时、分、秒
     * @return 设备时间
     */
    public static LocalDateTime decode(int[] registers) {
        if (registers == null || registers.length < REGISTER_COUNT) {
            throw new IllegalArgumentException("time registers must be " + REGISTER_COUNT);
        }
        for (int index = 0; index < REGISTER_COUNT; index++) {
            if (registers[index] < 0 || registers[index] > 65535) {
                throw new IllegalArgumentException(
                    "register value out of range: " + registers[index]);
            }
        }
        int year = registers[0];
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException("year out of range: " + year);
        }
        try {
            return LocalDateTime.of(year, registers[1], registers[2], registers[3], registers[4],
                registers[5]);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("invalid device time: " + e.getMessage(), e);
        }
    }
}
