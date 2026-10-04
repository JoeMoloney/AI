package dev.joe.aimemoryservice.domain.enums;

import java.util.Locale;

public enum MemoryType {
    FACT,
    DISCOVERY,
    DECISION,
    BUG,
    FAILURE,
    SUCCESS,
    WORKAROUND,
    COMMAND,
    ARCHITECTURE,
    HYPOTHESIS,
    LESSON,
    FILE_PURPOSE,
    FUNCTION_MEANING;

    public String databaseValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MemoryType fromDatabaseValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
