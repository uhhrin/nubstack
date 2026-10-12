package dev.nubstack.cull;

import java.util.Locale;
import java.util.Optional;

/** Which entity types cleanup and spawn cancelling may remove once a chunk is over its cap. */
public enum CullMode {
    ALL,
    HOSTILE,
    CUSTOM;

    public static Optional<CullMode> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
