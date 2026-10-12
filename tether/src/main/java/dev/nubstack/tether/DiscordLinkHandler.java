package dev.nubstack.tether;

import java.util.Objects;
import java.util.UUID;

/**
 * Discord-side link and unlink logic, independent of JDA so it can be unit tested.
 * Replies never contain the redeemed code.
 */
final class DiscordLinkHandler {

    interface Host {
        /** Called on the caller's thread after a successful link. Implementations must hop to the main thread. */
        void onLinked(UUID playerId, String discordId, String discordTag);

        String playerName(UUID playerId);
    }

    private final LinkService links;
    private final Host host;

    DiscordLinkHandler(LinkService links, Host host) {
        this.links = Objects.requireNonNull(links, "links");
        this.host = Objects.requireNonNull(host, "host");
    }

    String link(String rawCode, String discordId, String discordTag) {
        Objects.requireNonNull(discordId, "discordId");
        if (rawCode == null || rawCode.isBlank()) {
            return "Usage: /link code:<code> (or !link <code>)";
        }
        // shortcut: no per-user cooldown on guesses; 32^8 codes plus the TTL make brute force impractical,
        // add a cooldown if link.code-length is lowered
        LinkService.RedeemResult result = links.redeem(rawCode);
        if (!result.success()) {
            return DiscordText.redeemFailure(result.status());
        }
        UUID playerId = result.playerId();
        boolean replaced = links.linkByDiscordId(discordId)
                .map(row -> !row.playerId().equals(playerId))
                .orElse(false);
        links.link(playerId, discordId, discordTag);
        host.onLinked(playerId, discordId, discordTag);
        String reply = DiscordText.linkedReply(host.playerName(playerId));
        return replaced ? reply + " Your previous Minecraft link was replaced." : reply;
    }

    String unlink(String discordId) {
        Objects.requireNonNull(discordId, "discordId");
        return links.linkByDiscordId(discordId)
                .map(row -> {
                    links.unlink(row.playerId());
                    return "Unlinked from Minecraft account **"
                            + DiscordText.escapeMarkdown(host.playerName(row.playerId())) + "**.";
                })
                .orElse("Your Discord account is not linked to a Minecraft account.");
    }
}
