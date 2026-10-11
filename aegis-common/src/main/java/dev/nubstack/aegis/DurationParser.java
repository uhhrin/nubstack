package dev.nubstack.aegis;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses compact durations such as {@code 30s}, {@code 12h}, {@code 7d}.
 */
public final class DurationParser {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)([smhdw])$", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static Optional<Duration> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration '" + raw + "' (use 30s, 12h, 7d, 2w)");
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        return Optional.of(switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(amount * 7L);
            default -> throw new IllegalArgumentException("Invalid duration unit");
        });
    }
}
