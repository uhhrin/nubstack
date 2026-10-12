package dev.nubstack.spine.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Clickable command help with permission filtering and paging.
 */
public final class HelpBuilder {

    private final String root;
    private final List<Entry> entries = new ArrayList<>();

    private HelpBuilder(String root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    public static HelpBuilder root(String rootName) {
        return new HelpBuilder(rootName);
    }

    public HelpBuilder entry(String usage, String description, String permission) {
        entries.add(new Entry(
                Objects.requireNonNull(usage, "usage"),
                Objects.requireNonNull(description, "description"),
                permission));
        return this;
    }

    public Component build(Predicate<String> hasPermission, int page, int pageSize) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }

        List<Entry> visible = entries.stream()
                .filter(entry -> entry.permission() == null || hasPermission.test(entry.permission()))
                .toList();

        int from = Math.min((page - 1) * pageSize, visible.size());
        int to = Math.min(from + pageSize, visible.size());
        int totalPages = Math.max(1, (int) Math.ceil(visible.size() / (double) pageSize));

        Component header = Component.text()
                .append(Component.text("[", NamedTextColor.GRAY))
                .append(Component.text(root, NamedTextColor.AQUA))
                .append(Component.text("]", NamedTextColor.GRAY))
                .append(Component.space())
                .append(Component.text("Help", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" (" + page + "/" + totalPages + ")", NamedTextColor.DARK_GRAY))
                .build();

        Component body = Component.empty();
        for (Entry entry : visible.subList(from, to)) {
            Component line = Component.text()
                    .append(Component.newline())
                    .append(Component.text(entry.usage(), NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.suggestCommand(entry.usage()))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to suggest", NamedTextColor.GRAY))))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(entry.description(), NamedTextColor.GRAY))
                    .build();
            body = body.append(line);
        }
        if (visible.isEmpty()) {
            body = body.append(Component.newline())
                    .append(Component.text("No commands available.", NamedTextColor.GRAY));
        }
        return header.append(body);
    }

    private record Entry(String usage, String description, String permission) {
    }
}
