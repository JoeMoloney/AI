package dev.joe.aimemoryservice.domain.enums;

import java.util.Locale;

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
