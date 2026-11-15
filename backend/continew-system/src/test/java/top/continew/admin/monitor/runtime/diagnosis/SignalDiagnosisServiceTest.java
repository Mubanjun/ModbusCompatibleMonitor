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

package top.continew.admin.monitor.runtime.diagnosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** 信令诊断分类与建议的单元测试（FR-SIG-04）。 */
class SignalDiagnosisServiceTest {

    @Test
    void classifiesTimeout() {
        assertEquals(SignalDiagnosisService.CODE_TIMEOUT,
            SignalDiagnosisService.classify("重试 3 次后仍失败：RTU 收发失败：读取超时：期望 3 字节，实际仅收到 1 字节"));
    }

    @Test
    void classifiesNoResponse() {
        assertEquals(SignalDiagnosisService.CODE_NO_RESPONSE,
            SignalDiagnosisService.classify("读取超时：期望 3 字节，实际仅收到 0 字节"));
        assertEquals(SignalDiagnosisService.CODE_NO_RESPONSE,
            SignalDiagnosisService.classify("模拟从站无应答，读取超时"));
    }

    @Test
    void classifiesCrc() {
        assertEquals(SignalDiagnosisService.CODE_CRC,
            SignalDiagnosisService.classify("CRC 校验失败：01 03 04 02 E6 00 C7 5B EE"));
    }

    @Test
    void classifiesMisalignedFrames() {
        assertEquals(SignalDiagnosisService.CODE_MISALIGNED,
            SignalDiagnosisService.classify("应答寄存器数量与请求不符：请求 1 个，实际 2 个（字节数 4）"));
        assertEquals(SignalDiagnosisService.CODE_MISALIGNED,
            SignalDiagnosisService.classify("功能码不匹配：期望 6，实际 3"));
        assertEquals(SignalDiagnosisService.CODE_MISALIGNED,
            SignalDiagnosisService.classify("从站地址不匹配：期望 1，实际 5"));
    }

    @Test
    void classifiesSlaveExceptionAndChannel() {
        assertEquals(SignalDiagnosisService.CODE_SLAVE_EXCEPTION,
            SignalDiagnosisService.classify("设备返回异常响应，功能码 0x83，异常码 2（非法数据地址）"));
        assertEquals(SignalDiagnosisService.CODE_CHANNEL,
            SignalDiagnosisService
                .classify("打开通道失败：串口 COM11 @ 9600 8N1，Access to the path 'COM11' is denied"));
    }

    @Test
    void normalRowsHaveNoPhenomenon() {
        assertNull(SignalDiagnosisService.classify(null));
        assertNull(SignalDiagnosisService.classify(""));
        assertNull(SignalDiagnosisService.classify("   "));
    }

    @Test
    void everySuggestionMentionsAction() {
        for (String code : new String[] {SignalDiagnosisService.CODE_TIMEOUT,
            SignalDiagnosisService.CODE_NO_RESPONSE, SignalDiagnosisService.CODE_CRC,
            SignalDiagnosisService.CODE_MISALIGNED, SignalDiagnosisService.CODE_SLAVE_EXCEPTION,
            SignalDiagnosisService.CODE_CHANNEL, SignalDiagnosisService.CODE_ADDRESS_CONFLICT,
            SignalDiagnosisService.CODE_OTHER}) {
            String suggestion = SignalDiagnosisService.suggestion(code);
            assertEquals(true, suggestion != null && suggestion.length() > 10,
                code + " 的建议不能为空：" + suggestion);
        }
    }
}
