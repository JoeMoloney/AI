package dev.joe.aimemoryservice.domain.enums;

import java.util.Locale;

/** One-way lifecycle state; only {@link #ACTIVE} memories are searchable. */
public enum MemoryStatus {
    ACTIVE,
    SUPERSEDED,
    INVALIDATED,
    ARCHIVED;

    public String databaseValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MemoryStatus fromDatabaseValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
