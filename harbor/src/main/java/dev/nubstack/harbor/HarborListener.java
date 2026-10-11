
package dev.nubstack.harbor;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class HarborListener {

    private final HarborPlugin plugin;

    public HarborListener(HarborPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event) {
        RegisteredServer target = event.getOriginalServer();
        String name = target.getServerInfo().getName();
        if (plugin.health().isAvailable(name)) {
            Optional<QueueEntry> next = plugin.queues().poll(name);
            if (next.isPresent() && !next.get().playerId().equals(event.getPlayer().getUniqueId())) {
                // put back if we stole someone else's slot accidentally
                plugin.queues().enqueue(name, next.get());
            }
            return;
        }
        Player player = event.getPlayer();
        plugin.queues().enqueue(name, plugin.queues().entry(
                player.getUniqueId(), player.getUsername(), plugin.priorityFor(player)));
        int position = plugin.queues().position(name, player.getUniqueId());
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        player.sendMessage(Component.text(
                "Server busy. Queued for " + name + " at position #" + position,
                NamedTextColor.YELLOW));
    }
}
