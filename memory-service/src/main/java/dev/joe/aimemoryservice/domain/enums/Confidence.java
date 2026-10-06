package dev.joe.aimemoryservice.domain.enums;

import java.util.Locale;

/** Strength of evidence supporting a memory, from hypothesis to confirmed. */
public enum Confidence {
    HYPOTHESIS,
    LOW,
    MEDIUM,
    HIGH,
    CONFIRMED;

    public String databaseValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Confidence fromDatabaseValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
