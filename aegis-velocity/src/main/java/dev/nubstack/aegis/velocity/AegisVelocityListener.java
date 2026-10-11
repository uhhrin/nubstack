package dev.nubstack.aegis.velocity;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import dev.nubstack.aegis.AegisRuntime;
import dev.nubstack.aegis.Punishment;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class AegisVelocityListener {

    private final AegisRuntime runtime;

    public AegisVelocityListener(AegisRuntime runtime) {
        this.runtime = runtime;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Optional<Punishment> ban = runtime.moderation().activeBan(event.getPlayer().getUniqueId());
        if (ban.isEmpty()) {
            return;
        }
        String template = runtime.bannedMessage().replace("<reason>", ban.get().reason());
        event.setResult(ResultedEvent.ComponentResult.denied(MiniMessage.miniMessage().deserialize(template)));
    }
}
