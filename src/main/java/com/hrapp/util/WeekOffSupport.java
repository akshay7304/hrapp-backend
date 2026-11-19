package com.hrapp.util;

import com.hrapp.exception.BadRequestException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Company week-offs are stored in {@code company_settings.week_off_day} as a
 * comma-separated list of {@link DayOfWeek} names (e.g. {@code SATURDAY,SUNDAY}).
 * Older single-value rows (e.g. {@code SUNDAY}) still parse correctly.
 */
public final class WeekOffSupport {

    private WeekOffSupport() {
    }

    public static Set<DayOfWeek> parseDays(String stored) {
        Set<DayOfWeek> days = new LinkedHashSet<>();
        if (stored == null || stored.isBlank()) {
            return days;
        }
        for (String part : stored.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                days.add(DayOfWeek.valueOf(token.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // skip invalid tokens; callers may log
            }
        }
        return days;
    }

    public static List<String> toApiList(String stored) {
        List<String> names = new ArrayList<>();
        for (DayOfWeek day : parseDays(stored)) {
            names.add(day.name());
        }
        return names;
    }

    public static boolean isWeekOff(LocalDate date, String stored) {
        if (date == null) {
            return false;
        }
        return parseDays(stored).contains(date.getDayOfWeek());
    }

    /**
     * Normalizes FE input (array of day names) into storage form.
     * Accepts {@code Sunday}/{@code SUNDAY}/{@code sunday}.
     */
    public static String toStorage(List<String> weekOffDays) {
        if (weekOffDays == null || weekOffDays.isEmpty()) {
            throw new BadRequestException("At least one week-off day is required");
        }
        LinkedHashSet<DayOfWeek> unique = new LinkedHashSet<>();
        for (String raw : weekOffDays) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                unique.add(DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException(
                        "Invalid week-off day '" + raw + "'. Use MONDAY..SUNDAY");
            }
        }
        if (unique.isEmpty()) {
            throw new BadRequestException("At least one week-off day is required");
        }
        StringBuilder sb = new StringBuilder();
        for (DayOfWeek day : unique) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(day.name());
        }
        return sb.toString();
    }
}
