
package dev.nubstack.gatekeep;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import java.net.InetSocketAddress;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class GatekeepListener {

    private final GatekeepPlugin plugin;

    public GatekeepListener(GatekeepPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        InetSocketAddress address = event.getConnection().getRemoteAddress();
        String ip = address.getAddress().getHostAddress();
        if (plugin.guard().allow(ip)) {
            return;
        }
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                MiniMessage.miniMessage().deserialize(plugin.denyMessage())));
    }
}
