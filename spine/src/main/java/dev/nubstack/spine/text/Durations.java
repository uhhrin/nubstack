package dev.nubstack.spine.text;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compact duration parse/format helpers ({@code 7d} <-> {@code 7 days}).
 */
public final class Durations {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)([smhdw])$", Pattern.CASE_INSENSITIVE);

    private Durations() {
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

    public static String humanize(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero() || duration.isNegative()) {
            return "0 seconds";
        }
        long seconds = duration.getSeconds();
        if (seconds % 86_400L == 0) {
            long days = seconds / 86_400L;
            return days + (days == 1 ? " day" : " days");
        }
        if (seconds % 3_600L == 0) {
            long hours = seconds / 3_600L;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        if (seconds % 60L == 0) {
            long minutes = seconds / 60L;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }

    public static String remaining(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero() || duration.isNegative()) {
            return "0s";
        }
        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (days == 0 && hours == 0 && minutes == 0) {
            return seconds + "s";
        }
        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append('d');
        }
        if (hours > 0 || days > 0) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(hours).append('h');
        }
        if (minutes > 0 || days > 0 || hours > 0) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(minutes).append('m');
        }
        return out.toString();
    }
}
