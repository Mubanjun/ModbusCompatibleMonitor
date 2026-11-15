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

package top.continew.admin.common.config.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Jackson 时间反序列化兼容配置。
 *
 * <p>前端（Arco 日期选择器）与验收脚本习惯发送 `yyyy-MM-dd HH:mm:ss`，而 Jackson 默认只认
 * ISO-8601（`yyyy-MM-dd'T'HH:mm:ss`）；`@DateTimeFormat` 只对查询参数生效，对 `@RequestBody`
 * 无效。这里注册一个宽松的 LocalDateTime 反序列化器，两种格式都接受，
 * 避免"请求体里的时间解析失败"这类跨端联调问题（迁移向导的历史迁移/一致性校验曾因此失败）。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
@Configuration
public class JacksonConfiguration {

    /** 空格分隔的日期时间格式（前端/脚本常用） */
    private static final DateTimeFormatter SPACE_DATE_TIME = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss");

    /** 空格分隔的日期格式 */
    private static final DateTimeFormatter SPACE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 注册宽松的 LocalDateTime/LocalDate 反序列化器
     *
     * @return 定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer lenientJavaTimeCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer());
            module.addDeserializer(LocalDate.class, new LenientLocalDateDeserializer());
            builder.modulesToInstall(module);
        };
    }

    /**
     * 同时接受 ISO-8601 与「yyyy-MM-dd HH:mm:ss」的 LocalDateTime 反序列化器
     */
    static class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
            String text = parser.getText();
            if (text == null || text.isBlank()) {
                return null;
            }
            String value = text.trim();
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                // 继续尝试空格分隔格式
            }
            try {
                return LocalDateTime.parse(value, SPACE_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                // 只有日期时补零点
            }
            return LocalDate.parse(value, SPACE_DATE).atStartOfDay();
        }
    }

    /**
     * 同时接受 ISO 与「yyyy-MM-dd」的 LocalDate 反序列化器
     */
    static class LenientLocalDateDeserializer extends JsonDeserializer<LocalDate> {

        @Override
        public LocalDate deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
            String text = parser.getText();
            if (text == null || text.isBlank()) {
                return null;
            }
            String value = text.trim();
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException ignored) {
                return LocalDate.parse(value, SPACE_DATE);
            }
        }
    }
}
