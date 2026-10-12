package dev.nubstack.tether;

import java.util.Objects;

/**
 * Escapes untrusted text crossing the Discord bridge trust boundary.
 */
public final class DiscordText {

    private DiscordText() {
    }

    /**
     * Escapes Discord markdown metacharacters so player chat cannot bold, spoiler, or quote-inject.
     */
    public static String escapeMarkdown(String input) {
        Objects.requireNonNull(input, "input");
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\\', '*', '_', '~', '`', '|', '>', '#' -> out.append('\\').append(c);
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Neutralizes @everyone / @here / role-style pings in plaintext (AllowedMentions none is still required).
     */
    public static String neutralizeMentions(String input) {
        Objects.requireNonNull(input, "input");
        return input
                .replace("@everyone", "@\u200beveryone")
                .replace("@here", "@\u200bhere")
                .replace("<@", "<@\u200b");
    }

    /**
     * Formats a Minecraft chat line for Discord: escaped markdown, neutralized mentions.
     */
    public static String formatOutbound(String playerName, String message) {
        String safeName = neutralizeMentions(escapeMarkdown(playerName));
        String safeMessage = neutralizeMentions(escapeMarkdown(message));
        return "**" + safeName + "**: " + safeMessage;
    }

    public static String linkedReply(String playerName) {
        return "Linked to Minecraft account **" + escapeMarkdown(playerName) + "**.";
    }

    public static String redeemFailure(LinkService.RedeemStatus status) {
        Objects.requireNonNull(status, "status");
        return switch (status) {
            case UNKNOWN -> "That code is not valid. Run /tether link in game for a new one.";
            case EXPIRED -> "That code expired. Run /tether link in game for a new one.";
            case LOCKED -> "That code is locked after too many attempts. Run /tether link renew in game.";
            case OK -> throw new IllegalArgumentException("OK is not a failure");
        };
    }

    public static String truncate(String input, int max) {
        Objects.requireNonNull(input, "input");
        return input.length() <= max ? input : input.substring(0, max);
    }

    /**
     * Escapes MiniMessage special characters so Discord users cannot inject tags or click events in-game.
     */
    public static String escapeMiniMessage(String input) {
        Objects.requireNonNull(input, "input");
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' || c == '<' || c == '>') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
