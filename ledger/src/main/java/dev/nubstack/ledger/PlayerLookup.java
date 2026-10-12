package dev.nubstack.ledger;

import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Resolves players without blocking Mojang lookups.
 */
public final class PlayerLookup {

    private PlayerLookup() {
    }

    public static Optional<OfflinePlayer> resolve(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online);
        }
        // Paper: cached only; never triggers a blocking name->UUID fetch.
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
            return Optional.of(cached);
        }
        return Optional.empty();
    }
}
