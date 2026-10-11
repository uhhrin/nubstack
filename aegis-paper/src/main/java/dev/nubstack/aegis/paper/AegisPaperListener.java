package dev.nubstack.aegis.paper;

import dev.nubstack.aegis.AegisRuntime;
import dev.nubstack.aegis.Punishment;
import dev.nubstack.spine.text.Messages;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class AegisPaperListener implements Listener {

    private final JavaPlugin plugin;
    private final AegisRuntime runtime;

    public AegisPaperListener(JavaPlugin plugin, AegisRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID id = event.getUniqueId();
        Optional<Punishment> ban = runtime.moderation().activeBan(id);
        if (ban.isEmpty()) {
            return;
        }
        String template = runtime.bannedMessage().replace("<reason>", ban.get().reason());
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                Messages.parse(template));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Optional<Punishment> mute = runtime.moderation().activeMute(event.getPlayer().getUniqueId());
        if (mute.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        String template = runtime.mutedMessage().replace("<reason>", mute.get().reason());
        event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(template));
        plugin.getLogger().fine("Blocked muted chat from " + event.getPlayer().getName());
    }
}
