package dev.nubstack.amber;

import dev.nubstack.amber.SnapshotService.SnapshotRef;
import dev.nubstack.spine.text.HelpBuilder;
import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;

/**
 * Adventure builders for every clickable line amber prints.
 */
final class AmberMessages {

    static final String ADMIN = "amber.admin";

    private static final PrefixedMessages MSG = Messages.prefixed("amber");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private AmberMessages() {
    }

    static PrefixedMessages prefixed() {
        return MSG;
    }

    static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024d && unit < units.length - 1) {
            value /= 1024d;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    static Component help(Predicate<String> hasPermission, long diskUsedBytes) {
        HelpBuilder help = HelpBuilder.root("amber")
                .entry("/amber snapshot [world] [label]", "Snapshot a loaded world", ADMIN)
                .entry("/amber list [world]", "List snapshots, newest first", ADMIN)
                .entry("/amber info <file>", "Show snapshot details", ADMIN)
                .entry("/amber restore <file>", "Roll a world back (confirmed)", ADMIN)
                .entry("/amber delete <file>", "Delete a snapshot (confirmed)", ADMIN)
                .entry("/amber prune", "Apply retention now", ADMIN)
                .entry("/amber cancel", "Cancel a running snapshot", ADMIN)
                .entry("/amber reload", "Reload config.yml", ADMIN);
        return help.build(hasPermission, 1, 20)
                .append(Component.newline())
                .append(MSG.info("Snapshot disk used: " + formatSize(diskUsedBytes)));
    }

    static Component created(SnapshotRef ref) {
        String file = ref.path().getFileName().toString();
        return Component.text()
                .append(MSG.success("Snapshot saved: " + file + " (" + formatSize(ref.sizeBytes()) + ")"))
                .append(Component.space())
                .append(MSG.copyButton("Copy filename", file))
                .append(Component.space())
                .append(MSG.runButton("List", "/amber list " + ref.world()))
                .build();
    }

    static Component list(List<SnapshotRef> refs) {
        if (refs.isEmpty()) {
            return MSG.info("No snapshots");
        }
        Component out = MSG.info("Snapshots (" + refs.size() + "), newest first");
        for (SnapshotRef ref : refs) {
            out = out.append(Component.newline()).append(listLine(ref));
        }
        return out;
    }

    static Component listLine(SnapshotRef ref) {
        String file = ref.path().getFileName().toString();
        String detail = "World: " + ref.world()
                + "\nSize: " + formatSize(ref.sizeBytes())
                + "\nCreated: " + TIME.format(ref.createdAt())
                + (ref.label().isEmpty() ? "" : "\nLabel: " + ref.label());
        return Component.text()
                .append(MSG.hoverDetail(file, detail))
                .append(Component.space())
                .append(MSG.copyButton("Copy filename", file))
                .append(Component.space())
                .append(MSG.runButton("Restore", "/amber restore " + file))
                .append(Component.space())
                .append(MSG.runButton("Delete", "/amber delete " + file))
                .build();
    }

    static Component info(SnapshotRef ref) {
        String file = ref.path().getFileName().toString();
        return Component.text()
                .append(MSG.info("File: " + file))
                .append(Component.newline())
                .append(MSG.info("World: " + ref.world()))
                .append(Component.newline())
                .append(MSG.info("Label: " + (ref.label().isEmpty() ? "(none)" : ref.label())))
                .append(Component.newline())
                .append(MSG.info("Size: " + formatSize(ref.sizeBytes())))
                .append(Component.newline())
                .append(MSG.info("Created: " + TIME.format(ref.createdAt())))
                .append(Component.newline())
                .append(MSG.copyButton("Copy filename", file))
                .append(Component.space())
                .append(MSG.copyButton("Copy path", ref.path().toAbsolutePath().toString()))
                .build();
    }

    static Component restoreTypePrompt(SnapshotRef ref) {
        String file = ref.path().getFileName().toString();
        return Component.text()
                .append(MSG.warn("Restore " + file + " over world " + ref.world()
                        + "? Type the world name back to continue: /amber restore " + file + " " + ref.world()))
                .append(Component.space())
                .append(MSG.suggestButton("Type world", "/amber restore " + file + " " + ref.world()))
                .build();
    }
}
