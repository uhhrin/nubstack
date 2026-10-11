
package dev.nubstack.harbor;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerHealthRegistry {

    public record Health(boolean online, int players, int maxPlayers, Instant checkedAt) {}

    private final Map<String, Health> health = new ConcurrentHashMap<>();

    public void update(String server, boolean online, int players, int maxPlayers) {
        health.put(server, new Health(online, players, maxPlayers, Instant.now()));
    }

    public boolean isAvailable(String server) {
        Health value = health.get(server);
        if (value == null) {
            return true;
        }
        return value.online() && value.players() < value.maxPlayers();
    }

    public Health get(String server) {
        return health.get(server);
    }
}
