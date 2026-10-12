package dev.nubstack.harbor;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

public final class HarborListener {

    private final HarborPlugin plugin;

    public HarborListener(HarborPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event) {
        if (plugin.consumeBypass(event.getPlayer().getUniqueId())) {
            return;
        }
        RegisteredServer target = event.getOriginalServer();
        String name = target.getServerInfo().getName();
        if (plugin.queues().isPaused(name)) {
            queueAndDeny(event, name);
            return;
        }
        if (plugin.health().isAvailable(name)) {
            plugin.queues().leave(event.getPlayer().getUniqueId());
            plugin.clearBossBar(event.getPlayer());
            return;
        }
        queueAndDeny(event, name);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        plugin.queues().leave(player.getUniqueId());
        plugin.clearBossBar(player);
    }

    private void queueAndDeny(ServerPreConnectEvent event, String name) {
        Player player = event.getPlayer();
        QueueManager.EnqueueResult result = plugin.queues().enqueue(
                name,
                plugin.queues().entry(player.getUniqueId(), player.getUsername(), plugin.priorityFor(player)));
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        if (result == QueueManager.EnqueueResult.FULL) {
            player.sendMessage(HarborTexts.queueFull(name));
            return;
        }
        plugin.showQueued(player, name);
    }
}
