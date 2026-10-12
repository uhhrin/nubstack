package dev.nubstack.cull;

import dev.nubstack.spine.text.Durations;
import dev.nubstack.spine.text.HelpBuilder;
import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import java.time.Duration;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

/** Adventure components for cull help and scan results. */
public final class CullChat {

    private final PrefixedMessages msg = Messages.prefixed("cull");

    public Component help(Predicate<String> hasPermission, int globalMax, long nextCleanupSeconds) {
        Component header = msg.info("Max per chunk: " + globalMax
                + ". Next cleanup in " + Durations.remaining(Duration.ofSeconds(nextCleanupSeconds)));
        Component body = HelpBuilder.root("cull")
                .entry("/cull status [world]", "Caps, counters and chunks over cap", "cull.use")
                .entry("/cull scan [world] [limit]", "Rank chunks by entity count", "cull.use")
                .entry("/cull here", "Entities in your chunk", "cull.use")
                .entry("/cull tp <rank>", "Teleport to a scanned chunk", "cull.use")
                .entry("/cull run", "Run cleanup now", "cull.admin")
                .entry("/cull reload", "Reload config", "cull.admin")
                .build(hasPermission, 1, 8);
        return header.append(Component.newline()).append(body);
    }

    public Component resultLine(int rank, ChunkLoad load) {
        String label = "#" + rank + " " + load.world() + " " + load.chunkX() + "," + load.chunkZ()
                + " " + load.count() + "/" + load.cap();
        Component hotspot = msg.hoverDetail(label, load.breakdown())
                .clickEvent(ClickEvent.runCommand("/cull tp " + rank));
        return Component.text()
                .append(hotspot)
                .append(Component.space())
                .append(msg.copyButton("Copy coords", load.world() + " " + load.chunkX() + " " + load.chunkZ()))
                .build();
    }

    public Component info(String text) {
        return msg.info(text);
    }

    public Component success(String text) {
        return msg.success(text);
    }

    public Component warn(String text) {
        return msg.warn(text);
    }

    public Component error(String text) {
        return msg.error(text);
    }
}
