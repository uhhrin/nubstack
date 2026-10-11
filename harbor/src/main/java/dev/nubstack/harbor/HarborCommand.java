
package dev.nubstack.harbor;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class HarborCommand implements SimpleCommand {

    private final HarborPlugin plugin;

    public HarborCommand(HarborPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            invocation.source().sendMessage(Component.text("Usage: /harbor <leave|status>", NamedTextColor.YELLOW));
            return;
        }
        if ("leave".equalsIgnoreCase(args[0])) {
            if (!(invocation.source() instanceof Player player)) {
                invocation.source().sendMessage(Component.text("Players only", NamedTextColor.RED));
                return;
            }
            boolean removed = plugin.queues().leave(player.getUniqueId());
            invocation.source().sendMessage(Component.text(removed ? "Left queue" : "Not queued", NamedTextColor.GREEN));
            return;
        }
        if ("status".equalsIgnoreCase(args[0])) {
            String server = args.length > 1 ? args[1] : plugin.defaultServer();
            invocation.source().sendMessage(Component.text(
                    "Queue " + server + ": " + plugin.queues().size(server)
                            + " | healthy=" + plugin.health().isAvailable(server),
                    NamedTextColor.GRAY));
            return;
        }
        invocation.source().sendMessage(Component.text("Unknown subcommand", NamedTextColor.RED));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of("leave", "status");
    }
}
