package dev.nubstack.aegis;

import dev.nubstack.spine.text.Durations;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared argument parsing for Paper and Velocity {@code /aegis} commands.
 */
public final class AegisCommandParser {

    private AegisCommandParser() {
    }

    public static PunishArgs parsePunish(PunishmentType type, String[] args) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(args, "args");
        if (args.length < 2) {
            throw new ParseException("Usage: /aegis " + type.name().toLowerCase(Locale.ROOT)
                    + " <player> [duration|perm] <reason> [-s]");
        }
        String playerName = args[1];
        List<String> tokens = new ArrayList<>();
        boolean silent = false;
        for (int i = 2; i < args.length; i++) {
            if ("-s".equalsIgnoreCase(args[i])) {
                silent = true;
            } else {
                tokens.add(args[i]);
            }
        }
        if (tokens.isEmpty()) {
            throw new ParseException("Reason required");
        }

        Duration duration = null;
        boolean permanent = false;
        int reasonStart = 0;

        if (type != PunishmentType.WARN) {
            String first = tokens.getFirst();
            if ("perm".equalsIgnoreCase(first)) {
                if (tokens.size() < 2) {
                    throw new ParseException("Reason required");
                }
                permanent = true;
                reasonStart = 1;
            } else {
                try {
                    Optional<Duration> parsed = Durations.parse(first);
                    if (parsed.isPresent()) {
                        if (tokens.size() < 2) {
                            throw new ParseException("Reason required");
                        }
                        duration = parsed.get();
                        reasonStart = 1;
                    } else {
                        permanent = true;
                    }
                } catch (ParseException exception) {
                    throw exception;
                } catch (IllegalArgumentException ignored) {
                    // not a duration token; entire remainder is the reason (permanent)
                    permanent = true;
                }
            }
        }

        String reason = String.join(" ", tokens.subList(reasonStart, tokens.size()));
        if (reason.isBlank()) {
            throw new ParseException("Reason required");
        }
        if (type == PunishmentType.WARN) {
            permanent = false;
            duration = null;
        } else if (duration == null) {
            permanent = true;
        }
        return new PunishArgs(playerName, duration, reason, silent, permanent);
    }

    /** Page token at {@code args[2]} for {@code history <player> [page]}. */
    public static int parsePage(String[] args) {
        return parsePageAt(args, 2);
    }

    /** Page token at {@code args[1]} for {@code help [page]}. */
    public static int parseHelpPage(String[] args) {
        return parsePageAt(args, 1);
    }

    public static int parsePageAt(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            int page = Integer.parseInt(args[index]);
            return Math.max(1, page);
        } catch (NumberFormatException exception) {
            throw new ParseException("Page must be a positive integer");
        }
    }

    public static Optional<UUID> parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record PunishArgs(
            String playerName,
            Duration duration,
            String reason,
            boolean silent,
            boolean permanent) {
    }

    public static final class ParseException extends IllegalArgumentException {
        public ParseException(String message) {
            super(message);
        }
    }
}
