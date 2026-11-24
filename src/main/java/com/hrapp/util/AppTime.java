package com.hrapp.util;

import com.hrapp.config.JacksonConfig;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Business clock for the HR app — always Asia/Kolkata (IST).
 * Prefer this over bare {@code LocalDate.now()} / {@code LocalDateTime.now()}
 * so cloud hosts on UTC do not shift attendance days and schedulers.
 */
public final class AppTime {

    public static final ZoneId ZONE = JacksonConfig.APP_ZONE;

    private AppTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static int currentYear() {
        return today().getYear();
    }
}
