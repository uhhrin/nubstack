package dev.nubstack.cairn.grave;

import java.util.Locale;
import java.util.Objects;

public enum XpPolicy {
    /** Vanilla behavior: experience orbs drop where the player died. */
    DROP("drop"),
    /** The player respawns with the experience they had. */
    KEEP("keep");

    private final String configName;

    XpPolicy(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }

    public static XpPolicy parse(String value) {
        String normalized = Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT);
        for (XpPolicy policy : values()) {
            if (policy.configName.equals(normalized)) {
                return policy;
            }
        }
        throw new IllegalArgumentException("xpPolicy must be drop or keep, got: " + value);
    }
}
