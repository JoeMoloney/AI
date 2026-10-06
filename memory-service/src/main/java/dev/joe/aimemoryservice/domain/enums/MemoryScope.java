package dev.joe.aimemoryservice.domain.enums;

import java.util.Locale;

/** Controls whether a memory is global or associated with a project/session. */
public enum MemoryScope {
    GLOBAL,
    PROJECT,
    SESSION;

    public String databaseValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MemoryScope fromDatabaseValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
