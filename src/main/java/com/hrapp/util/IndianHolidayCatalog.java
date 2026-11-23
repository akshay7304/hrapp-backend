package com.hrapp.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gazetted Indian holidays for the admin multi-select.
 * Dates follow the DoPT Central Government lists for Delhi:
 * 2025 (OM 9 July 2024), 2026 (OM 3 July 2025), 2027 (OM 16 July 2026).
 * Lunar festivals move every year, so a preset is always for one year.
 */
public final class IndianHolidayCatalog {

    public record Preset(String code, String name, LocalDate holidayDate) {
    }

    private static final Map<Integer, List<Preset>> BY_YEAR;

    static {
        Map<Integer, List<Preset>> years = new LinkedHashMap<>();
        years.put(2025, List.of(
                preset("REPUBLIC_DAY", "Republic Day", 2025, 1, 26),
                preset("HOLI", "Holi", 2025, 3, 14),
                preset("ID_UL_FITR", "Id-ul-Fitr", 2025, 3, 31),
                preset("RAM_NAVAMI", "Ram Navami", 2025, 4, 6),
                preset("MAHAVIR_JAYANTI", "Mahavir Jayanti", 2025, 4, 10),
                preset("GOOD_FRIDAY", "Good Friday", 2025, 4, 18),
                preset("BUDDHA_PURNIMA", "Buddha Purnima", 2025, 5, 12),
                preset("ID_UL_ZUHA", "Id-ul-Zuha (Bakrid)", 2025, 6, 7),
                preset("MUHARRAM", "Muharram", 2025, 7, 6),
                preset("INDEPENDENCE_DAY", "Independence Day", 2025, 8, 15),
                preset("JANMASHTAMI", "Janmashtami", 2025, 8, 16),
                preset("MILAD_UN_NABI", "Milad-un-Nabi", 2025, 9, 5),
                preset("GANDHI_JAYANTI", "Gandhi Jayanti", 2025, 10, 2),
                preset("DUSSEHRA", "Dussehra", 2025, 10, 2),
                preset("DIWALI", "Diwali", 2025, 10, 20),
                preset("GURU_NANAK_JAYANTI", "Guru Nanak Jayanti", 2025, 11, 5),
                preset("CHRISTMAS", "Christmas", 2025, 12, 25)
        ));
        years.put(2026, List.of(
                preset("REPUBLIC_DAY", "Republic Day", 2026, 1, 26),
                preset("HOLI", "Holi", 2026, 3, 4),
                preset("ID_UL_FITR", "Id-ul-Fitr", 2026, 3, 21),
                preset("RAM_NAVAMI", "Ram Navami", 2026, 3, 26),
                preset("MAHAVIR_JAYANTI", "Mahavir Jayanti", 2026, 3, 31),
                preset("GOOD_FRIDAY", "Good Friday", 2026, 4, 3),
                preset("BUDDHA_PURNIMA", "Buddha Purnima", 2026, 5, 1),
                preset("ID_UL_ZUHA", "Id-ul-Zuha (Bakrid)", 2026, 5, 27),
                preset("MUHARRAM", "Muharram", 2026, 6, 26),
                preset("INDEPENDENCE_DAY", "Independence Day", 2026, 8, 15),
                preset("MILAD_UN_NABI", "Milad-un-Nabi", 2026, 8, 26),
                preset("JANMASHTAMI", "Janmashtami", 2026, 9, 4),
                preset("GANDHI_JAYANTI", "Gandhi Jayanti", 2026, 10, 2),
                preset("DUSSEHRA", "Dussehra", 2026, 10, 20),
                preset("DIWALI", "Diwali", 2026, 11, 8),
                preset("GURU_NANAK_JAYANTI", "Guru Nanak Jayanti", 2026, 11, 24),
                preset("CHRISTMAS", "Christmas", 2026, 12, 25)
        ));
        years.put(2027, List.of(
                preset("REPUBLIC_DAY", "Republic Day", 2027, 1, 26),
                preset("ID_UL_FITR", "Id-ul-Fitr", 2027, 3, 10),
                preset("HOLI", "Holi", 2027, 3, 23),
                preset("GOOD_FRIDAY", "Good Friday", 2027, 3, 26),
                preset("RAM_NAVAMI", "Ram Navami", 2027, 4, 15),
                preset("MAHAVIR_JAYANTI", "Mahavir Jayanti", 2027, 4, 19),
                preset("ID_UL_ZUHA", "Id-ul-Zuha (Bakrid)", 2027, 5, 17),
                preset("BUDDHA_PURNIMA", "Buddha Purnima", 2027, 5, 20),
                preset("MUHARRAM", "Muharram", 2027, 6, 16),
                preset("INDEPENDENCE_DAY", "Independence Day", 2027, 8, 15),
                preset("MILAD_UN_NABI", "Milad-un-Nabi", 2027, 8, 15),
                preset("JANMASHTAMI", "Janmashtami", 2027, 8, 25),
                preset("GANDHI_JAYANTI", "Gandhi Jayanti", 2027, 10, 2),
                preset("DUSSEHRA", "Dussehra", 2027, 10, 9),
                preset("DIWALI", "Diwali", 2027, 10, 29),
                preset("GURU_NANAK_JAYANTI", "Guru Nanak Jayanti", 2027, 11, 14),
                preset("CHRISTMAS", "Christmas", 2027, 12, 25)
        ));
        BY_YEAR = Collections.unmodifiableMap(years);
    }

    private IndianHolidayCatalog() {
    }

    public static boolean supports(int year) {
        return BY_YEAR.containsKey(year);
    }

    public static String supportedYearLabel() {
        List<Integer> years = new ArrayList<>(BY_YEAR.keySet());
        if (years.size() == 1) {
            return String.valueOf(years.get(0));
        }
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < years.size(); i++) {
            if (i > 0 && i == years.size() - 1) {
                label.append(" and ");
            } else if (i > 0) {
                label.append(", ");
            }
            label.append(years.get(i));
        }
        return label.toString();
    }

    public static List<Preset> forYear(int year) {
        List<Preset> presets = BY_YEAR.get(year);
        return presets == null ? List.of() : presets;
    }

    public static Optional<Preset> find(int year, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim();
        return forYear(year).stream()
                .filter(preset -> preset.code().equalsIgnoreCase(normalized))
                .findFirst();
    }

    private static Preset preset(String code, String name, int year, int month, int day) {
        return new Preset(code, name, LocalDate.of(year, month, day));
    }
}
