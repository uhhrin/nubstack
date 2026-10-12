package dev.nubstack.spotlight;

import dev.nubstack.spine.text.HelpBuilder;
import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Adventure components for spotlight scan results and help.
 */
public final class SpotlightChat {

    private final PrefixedMessages msg = Messages.prefixed("spotlight");
    private final ChunkWeights weights;

    public SpotlightChat(ChunkWeights weights) {
        this.weights = weights;
    }

    public Component help(Predicate<String> hasPermission, String lastScanAge) {
        Component header = msg.info("Last scan: " + lastScanAge);
        Component body = HelpBuilder.root("spotlight")
                .entry("/spotlight scan [world] [limit]", "Rank loaded chunks", "spotlight.scan")
                .entry("/spotlight here", "Score your current chunk", "spotlight.scan")
                .entry("/spotlight near [radius]", "Local hot chunks", "spotlight.scan")
                .entry("/spotlight tp <rank>", "Teleport to a ranked result", "spotlight.tp")
                .entry("/spotlight book", "Open last scan as a book", "spotlight.scan")
                .entry("/spotlight weights", "Show score weights", "spotlight.admin")
                .entry("/spotlight reload", "Reload config", "spotlight.admin")
                .build(hasPermission, 1, 9);
        return header.append(Component.newline()).append(body);
    }

    public Component resultLine(int rank, LagReport.ChunkCost cost, boolean canTp) {
        String coords = cost.world() + " " + cost.chunkX() + " " + cost.chunkZ();
        String label = "#" + rank + " " + cost.world() + " " + cost.chunkX() + "," + cost.chunkZ()
                + " score=" + cost.score();
        Component hotspot = Component.text(label, NamedTextColor.WHITE)
                .hoverEvent(HoverEvent.showText(
                        Component.text(cost.explain(weights), NamedTextColor.GRAY)))
                .clickEvent(canTp
                        ? ClickEvent.runCommand("/spotlight tp " + rank)
                        : ClickEvent.suggestCommand(
                                "/tp @s " + (cost.chunkX() * 16 + 8) + " ~ " + (cost.chunkZ() * 16 + 8)));
        return Component.text()
                .append(hotspot)
                .append(Component.space())
                .append(msg.copyButton("Copy coords", coords))
                .build();
    }

    public Component afterScanButtons() {
        return Component.text()
                .append(msg.runButton("Rescan", "/spotlight scan"))
                .append(Component.space())
                .append(msg.runButton("Book view", "/spotlight book"))
                .build();
    }

    public Component scanHeader(int shown, int total, long elapsedMs) {
        return msg.success("Top " + shown + " of " + total + " scored chunks (" + elapsedMs + " ms)");
    }

    public List<Component> bookPages(List<LagReport.ChunkCost> costs) {
        if (costs.isEmpty()) {
            return List.of(Component.text("No scored chunks.", NamedTextColor.GRAY));
        }
        List<Component> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int onPage = 0;
        int rank = 1;
        for (LagReport.ChunkCost cost : costs) {
            String line = "#" + rank + " " + cost.world() + " " + cost.chunkX() + "," + cost.chunkZ()
                    + "\n" + cost.explain(weights) + "\n\n";
            if (onPage >= 6) {
                pages.add(Component.text(page.toString()));
                page = new StringBuilder();
                onPage = 0;
            }
            page.append(line);
            onPage++;
            rank++;
        }
        if (!page.isEmpty()) {
            pages.add(Component.text(page.toString()));
        }
        return pages;
    }

    public Component weightsLine(ChunkWeights w) {
        return msg.info("entities=" + w.entities()
                + " hoppers=" + w.hoppers()
                + " minecarts=" + w.minecarts()
                + " redstone=" + w.redstone()
                + " item-frames=" + w.itemFrames()
                + " villagers=" + w.villagers());
    }
}
