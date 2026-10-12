package dev.nubstack.cairn.grave;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public enum AccessMode {
    OWNER("owner"),
    PUBLIC("public");

    private final String configName;

    AccessMode(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }

    public boolean allows(UUID owner, UUID actor, boolean admin) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(actor, "actor");
        return this == PUBLIC || admin || owner.equals(actor);
    }

    public static AccessMode parse(String value) {
        String normalized = Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT);
        for (AccessMode mode : values()) {
            if (mode.configName.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("accessMode must be owner or public, got: " + value);
    }
}
