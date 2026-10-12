package dev.nubstack.gatekeep;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;

public final class GatekeepListener {

    private final GatekeepPlugin plugin;

    public GatekeepListener(GatekeepPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        int zone = ip.indexOf('%');
        if (zone >= 0) {
            ip = ip.substring(0, zone);
        }
        ConnectionGuard.Decision decision = plugin.guard().check(ip);
        if (decision.allowed()) {
            return;
        }
        plugin.logger().info(decision.logLine(ip));
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                GatekeepTexts.denyMessage(plugin.denyMessage(), decision)));
    }
}
