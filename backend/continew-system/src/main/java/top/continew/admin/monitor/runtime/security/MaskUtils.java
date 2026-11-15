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

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感字段脱敏（NFR-SEC-07）
 *
 * <p>两套用法：①按**字段名**脱敏（导出 CSV/Excel、拼接 JSON 报文时用）；
 * ②按**内容**扫描脱敏（日志与自由文本里出现手机号/身份证/邮箱/银行卡时兜底）。
 * all 方法都是纯函数，便于单测与复用。</p>
 *
 * <p>脱敏只保留必要的可辨识片段（手机号保留前 3 后 4、邮箱保留首字符与域名），
 * 既能定位问题又不泄露完整信息。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
public final class MaskUtils {

    /** 完全遮蔽时使用的占位符 */
    public static final String MASK = "******";

    /** 按字段名识别银行卡 */
    private static final Set<String> BANK_CARD_FIELDS = Set.of("bankcard", "cardno", "cardnumber",
        "bankaccount");

    /** 手机号（大陆） */
    private static final Pattern PHONE =
        Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");

    /** 身份证（18 位，末位可能是 X） */
    private static final Pattern ID_CARD =
        Pattern.compile("(?<!\\w)(\\d{4})\\d{10}([0-9Xx]{4})(?!\\w)");

    /** 邮箱：保留首字符与域名 */
    private static final Pattern EMAIL =
        Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    /** 银行卡/长数字串：保留前 4 后 4 */
    private static final Pattern BANK_CARD =
        Pattern.compile("(?<!\\d)(\\d{4})\\d{8,11}(\\d{4})(?!\\d)");

    /** 需要按字段名遮蔽的字段（密码、令牌、密钥一律不留片段）；集合内一律写成**归一化**形式（小写、无分隔符） */
    private static final Set<String> SECRET_FIELDS = Set.of("password", "passwd", "pwd",
        "oldpassword",
        "newpassword", "secret", "token", "accesstoken", "refreshtoken", "privatekey", "appsecret",
        // 缺陷 68：补齐常见的密钥/凭证字段名，否则按字段名导出时会漏脱敏
        "apikey", "accesskey", "accesskeyid", "secretkey", "clientsecret", "authorization");

    /** 按字段名识别手机号（同样使用归一化形式：缺陷 68 前写成 phoneNumber/mobileNumber，归一化后永远匹配不上） */
    private static final Set<String> PHONE_FIELDS = Set.of("phone", "mobile", "tel", "telephone",
        "contact", "phonenumber", "mobilenumber");

    /** 按字段名识别身份证 */
    // 注意不要把 cardNo 放进身份证集合：它既可能是银行卡号也可能是身份证号，
    // 放到 BANK_CARD_FIELDS 里按「保留前 4 后 4」处理更通用
    private static final Set<String> ID_FIELDS =
        Set.of("idcard", "idno", "certno", "idnumber", "identity");

    /** 按字段名识别邮箱 */
    private static final Set<String> EMAIL_FIELDS = Set.of("email", "mail", "mailbox");

    private MaskUtils() {
    }

    /**
     * 手机号脱敏：13812345678 → 138****5678
     *
     * @param value 原始值
     * @return 脱敏后的值；非手机号原样返回
     */
    public static String maskPhone(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        Matcher matcher = PHONE.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer,
                Matcher.quoteReplacement(matcher.group(1) + "****" + matcher
                    .group(2)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * 身份证脱敏：110101199003071234 → 1101**********1234
     *
     * @param value 原始值
     * @return 脱敏后的值
     */
    public static String maskIdCard(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        Matcher matcher = ID_CARD.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            // 身份证第 7~14 位必须是合法出生日期，否则不按身份证处理：
            // 平台自身的 18 位雪花 ID 也是 18 位数字，只有日期校验才能把它排除，
            // 否则日志里的 id=... 会被打成 id=****，排障时无法定位
            if (!plausibleBirthday(matcher.group(0).substring(6, 14))) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            matcher.appendReplacement(buffer,
                Matcher.quoteReplacement(matcher.group(1) + "**********"
                    + matcher.group(2)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * 邮箱脱敏：zhangsan@example.com → z*******@example.com
     *
     * @param value 原始值
     * @return 脱敏后的值
     */
    public static String maskEmail(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        Matcher matcher = EMAIL.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer,
                Matcher.quoteReplacement(matcher.group(1) + "*******" + matcher
                    .group(2)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * 银行卡脱敏：6222021234567890123 → 6222***********0123
     *
     * @param value 原始值
     * @return 脱敏后的值
     */
    public static String maskBankCard(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        Matcher matcher = BANK_CARD.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer,
                Matcher.quoteReplacement(matcher.group(1) + "***********"
                    + matcher.group(2)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * 文本级兜底：把自由文本里的手机号/身份证/邮箱/银行卡全部脱敏（日志与诊断包用）
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public static String maskText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 文本级只处理形态明确的手机号/身份证/邮箱；银行卡不做文本级匹配
        // （16~19 位纯数字与平台雪花 ID 无法区分，误伤日志比漏脱敏更糟），银行卡按字段名走
        String masked = maskIdCard(text);
        masked = maskPhone(masked);
        return maskEmail(masked);
    }

    /**
     * 按字段名脱敏单个值
     *
     * @param field 字段名（大小写、下划线不敏感）
     * @param value 字段值
     * @return 脱敏后的值（字符串形式）；字段不敏感时原样返回
     */
    public static Object maskField(String field, Object value) {
        if (value == null) {
            return null;
        }
        // 缺陷 68：字段名归一化必须同时去掉下划线与连字符（phone_number / phone-number / PHONE_NUMBER
        // 在此之前都匹配不上），且集合内也必须用同样的归一化形式，否则 camelCase 条目永远命中不了。
        String name =
            field == null ? "" : field.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (SECRET_FIELDS.contains(name)) {
            return MASK;
        }
        String text = String.valueOf(value);
        if (PHONE_FIELDS.contains(name)) {
            return maskPhone(text);
        }
        if (ID_FIELDS.contains(name)) {
            return maskIdCard(text);
        }
        if (EMAIL_FIELDS.contains(name)) {
            return maskEmail(text);
        }
        if (BANK_CARD_FIELDS.contains(name)) {
            return maskBankCard(text);
        }
        return value;
    }

    /**
     * 身份证出生日期段（yyyyMMdd）是否合法
     *
     * @param digits 8 位日期字符串
     * @return 是否合法
     */
    private static boolean plausibleBirthday(String digits) {
        try {
            int year = Integer.parseInt(digits.substring(0, 4));
            int month = Integer.parseInt(digits.substring(4, 6));
            int day = Integer.parseInt(digits.substring(6, 8));
            if (year < 1900 || year > 2100 || month < 1 || month > 12) {
                return false;
            }
            return LocalDate.of(year, month, day).getDayOfMonth() == day;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 按列名脱敏一行导出数据（CSV/Excel 导出共用）
     *
     * @param headers 列名
     * @param values 该行取值，长度与列名一致
     * @return 脱敏后的取值
     */
    public static String[] maskRow(List<String> headers, String[] values) {
        if (headers == null || values == null) {
            return values;
        }
        String[] masked = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            Object value = i < headers.size() ? maskField(headers.get(i), values[i]) : values[i];
            masked[i] = value == null ? null : String.valueOf(value);
        }
        return masked;
    }
}
