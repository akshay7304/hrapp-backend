package com.hrapp.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.TimeZone;

/**
 * Company clocks are IST. {@link LocalDateTime} in the API means <em>IST wall
 * clock</em> (no separate TZ column).
 * <p>
 * Web clients often send {@code ...Z} or {@code ...+05:30} from {@code Date}.
 * Without normalization those become UTC wall times (e.g. 08:00 IST → 02:30),
 * which matches the regularization bug report. This config always lands on IST.
 */
@Configuration
public class JacksonConfig {

    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    @Bean
    Jackson2ObjectMapperBuilderCustomizer istDateTimeCustomizer() {
        return builder -> {
            builder.timeZone(TimeZone.getTimeZone(APP_ZONE));
            builder.modulesToInstall(istJavaTimeModule());
        };
    }

    private static JavaTimeModule istJavaTimeModule() {
        JavaTimeModule module = new JavaTimeModule();
        module.addDeserializer(LocalDateTime.class, new IstLocalDateTimeDeserializer());
        module.addSerializer(LocalDateTime.class, new IstLocalDateTimeSerializer());
        return module;
    }

    /**
     * Accepts naive local, offset, or instant strings; always yields IST wall clock.
     */
    static final class IstLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            text = text.trim();

            // 1) Naive local — treat as already IST wall clock
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                // fall through
            }

            // 2) Instant (...Z)
            try {
                Instant instant = Instant.parse(text);
                return LocalDateTime.ofInstant(instant, APP_ZONE);
            } catch (DateTimeParseException ignored) {
                // fall through
            }

            // 3) Offset / zoned
            try {
                return OffsetDateTime.parse(text).atZoneSameInstant(APP_ZONE).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // fall through
            }
            try {
                return ZonedDateTime.parse(text).withZoneSameInstant(APP_ZONE).toLocalDateTime();
            } catch (DateTimeException ex) {
                return (LocalDateTime) ctxt.handleWeirdStringValue(
                        LocalDateTime.class,
                        text,
                        "Expected ISO local, offset, or instant date-time");
            }
        }
    }

    /**
     * Emit IST wall clock without offset so the web app displays the same
     * numbers HR entered (serialization stays stable for existing clients).
     */
    static final class IstLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
    }
}
