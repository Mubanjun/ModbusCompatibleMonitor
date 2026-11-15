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

package top.continew.admin.monitor.runtime.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感字段脱敏单测（NFR-SEC-07，纯逻辑不依赖 Spring）
 *
 * @author jdrk
 * @since 2026/9/25
 */
class MaskUtilsTest {

    @Test
    void phoneShouldKeepHeadAndTail() {
        assertEquals("138****5678", MaskUtils.maskPhone("13812345678"));
        assertEquals("联系电话 138****5678。", MaskUtils.maskPhone("联系电话 13812345678。"));
        assertEquals("1381234567", MaskUtils.maskPhone("1381234567"), "位数不足不改动");
        assertEquals("213812345678", MaskUtils.maskPhone("213812345678"), "前面还有数字时不算手机号");
    }

    @Test
    void idCardShouldKeepHeadFourAndTailFour() {
        assertEquals("1101**********1234", MaskUtils.maskIdCard("110101199003071234"));
        assertEquals("1101**********123X", MaskUtils.maskIdCard("11010119900307123X"));
    }

    @Test
    void emailShouldKeepFirstCharAndDomain() {
        assertEquals("z*******@example.com", MaskUtils.maskEmail("zhangsan@example.com"));
        assertEquals("a*******@qq.com", MaskUtils.maskEmail("a@qq.com"));
    }

    @Test
    void bankCardShouldKeepHeadFourAndTailFour() {
        assertEquals("6222***********0123", MaskUtils.maskBankCard("6222021234567890123"));
    }

    @Test
    void maskTextShouldCoverAllKindsInOnePass() {
        String text =
            "用户 13812345678（z*******@example.com 已掩码）身份证 110101199003071234 卡号 6222021234567890123";
        String masked = MaskUtils.maskText(text);
        assertFalse(masked.contains("13812345678"), masked);
        assertFalse(masked.contains("110101199003071234"), masked);
        assertTrue(masked.contains("138****5678"), masked);
        assertTrue(masked.contains("1101**********1234"), masked);
        // 银行卡不做文本级匹配：16~19 位纯数字与平台雪花 ID 无法区分，误伤日志代价更高
        assertTrue(masked.contains("6222021234567890123"), masked);
    }

    @Test
    void snowflakeIdShouldNotBeMaskedAsIdCard() {
        // 平台主键是 18/19 位雪花 ID，第 7~14 位不是合法出生日期，必须保持原样才能在日志里定位
        assertEquals("893940480491278342", MaskUtils.maskIdCard("893940480491278342"));
        assertEquals("89394048049127834", MaskUtils.maskIdCard("89394048049127834"));
    }

    @Test
    void bankCardShouldBeMaskedByFieldName() {
        assertEquals("6222***********0123", MaskUtils.maskField("cardNo", "6222021234567890123"));
        assertEquals("6222***********0123", MaskUtils.maskField("bankCard", "6222021234567890123"));
    }

    @Test
    void fieldNameShouldBeNormalizedAcrossSeparatorsAndCase() {
        // 缺陷 68：下划线/连字符/大小写三种写法都必须命中，否则按字段名导出会漏脱敏
        assertEquals("138****5678", MaskUtils.maskField("phone_number", "13812345678"));
        assertEquals("138****5678", MaskUtils.maskField("phone-number", "13812345678"));
        assertEquals("138****5678", MaskUtils.maskField("PHONE_NUMBER", "13812345678"));
        assertEquals("138****5678", MaskUtils.maskField("phoneNumber", "13812345678"));
        assertEquals("138****5678", MaskUtils.maskField("mobile_number", "13812345678"));
        assertEquals("1101**********1234", MaskUtils.maskField("id_number", "110101199003071234"));
    }

    @Test
    void secretFieldNamesShouldBeMaskedWhole() {
        // 缺陷 68：api key / access key / client secret / authorization 原先都不在集合里
        for (String field : new String[] {"password", "new_password", "apiKey", "api-key",
            "accessKey",
            "access-key", "secretKey", "secret-key", "clientSecret", "private_key", "authorization",
            "app_secret", "refresh-token"}) {
            assertEquals(MaskUtils.MASK, MaskUtils.maskField(field, "s3cr3t-value"), field);
        }
    }

    @Test
    void maskTextShouldIgnoreNullOrEmpty() {
        assertNull(MaskUtils.maskText(null));
        assertEquals("", MaskUtils.maskText(""));
        assertEquals("普通日志消息", MaskUtils.maskText("普通日志消息"));
    }

    @Test
    void maskFieldShouldUseFieldName() {
        assertEquals(MaskUtils.MASK, MaskUtils.maskField("password", "Admin@123"));
        assertEquals(MaskUtils.MASK, MaskUtils.maskField("access_token", "abc"));
        assertEquals("138****5678", MaskUtils.maskField("phone", "13812345678"));
        assertEquals("138****5678", MaskUtils.maskField("mobile", "13812345678"));
        assertEquals("1101**********1234", MaskUtils.maskField("idCard", "110101199003071234"));
        assertEquals("z*******@example.com", MaskUtils.maskField("email", "zhangsan@example.com"));
        assertEquals("水位", MaskUtils.maskField("elementName", "水位"), "不敏感字段保持原值");
        assertNull(MaskUtils.maskField("phone", null));
    }

    @Test
    void maskRowShouldApplyByHeader() {
        List<String> headers = List.of("deviceName", "phone", "email");
        String[] masked = MaskUtils.maskRow(headers,
            new String[] {"1 号站", "13812345678", "zhangsan@example.com"});
        assertEquals("1 号站", masked[0]);
        assertEquals("138****5678", masked[1]);
        assertEquals("z*******@example.com", masked[2]);
    }

    @Test
    void maskRowShouldTolerateMissingHeader() {
        String[] masked = MaskUtils.maskRow(List.of("a"), new String[] {"x", "y"});
        assertEquals("x", masked[0]);
        assertEquals("y", masked[1]);
    }
}
