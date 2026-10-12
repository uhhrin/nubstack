package dev.nubstack.gatekeep;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.nubstack.spine.text.ConfirmChat;
import dev.nubstack.spine.text.Durations;
import dev.nubstack.spine.text.HelpBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;

public final class GatekeepCommand implements SimpleCommand {

    static final String ADMIN = "gatekeep.admin";
    private static final int STATUS_LIMIT = 10;

    private final GatekeepPlugin plugin;

    public GatekeepCommand(GatekeepPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission(ADMIN)) {
            source.sendMessage(GatekeepTexts.MSG.error("No permission."));
            return;
        }
        String[] args = invocation.arguments();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> source.sendMessage(help(source));
            case "status" -> status(source);
            case "check" -> check(source, args);
            case "clear" -> clear(source, args);
            case "confirm" -> resolve(source, args, true);
            case "cancel" -> resolve(source, args, false);
            case "reload" -> reload(source);
            default -> source.sendMessage(GatekeepTexts.MSG.error("Unknown subcommand. Try /gatekeep help."));
        }
    }

    private void status(CommandSource source) {
        ConnectionGuard guard = plugin.guard();
        ConnectionGuard.Settings settings = guard.settings();
        source.sendMessage(GatekeepTexts.MSG.info(
                "Tracking " + guard.trackedCount() + " addresses, " + guard.deniedTotal()
                        + " denied since start. Limit " + settings.maxAttempts() + " per "
                        + Durations.humanize(settings.window()) + "."));
        for (ConnectionGuard.Entry entry : guard.snapshots(STATUS_LIMIT)) {
            source.sendMessage(GatekeepTexts.statusEntry(entry, settings.maxAttempts()));
        }
    }

    private void check(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(GatekeepTexts.MSG.warn("Usage: /gatekeep check <ip>"));
            return;
        }
        String ip = parseIp(source, args[1]);
        if (ip == null) {
            return;
        }
        ConnectionGuard guard = plugin.guard();
        if (guard.isExempt(ip)) {
            source.sendMessage(GatekeepTexts.MSG.info(ip + " is exempt and never throttled."));
            return;
        }
        Optional<ConnectionGuard.Entry> entry = guard.inspect(ip);
        if (entry.isEmpty()) {
            source.sendMessage(GatekeepTexts.MSG.info(ip + " has no recent attempts."));
            return;
        }
        ConnectionGuard.Entry found = entry.get();
        source.sendMessage(GatekeepTexts.statusEntry(found, guard.settings().maxAttempts()));
        source.sendMessage(Component.text()
                .append(GatekeepTexts.MSG.info(
                        "Support code " + ConnectionGuard.supportCode(found.key()) + " "))
                .append(GatekeepTexts.MSG.copyButton("Copy", ConnectionGuard.supportCode(found.key())))
                .build());
    }

    private void clear(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(GatekeepTexts.MSG.warn("Usage: /gatekeep clear <ip|*>"));
            return;
        }
        if ("*".equals(args[1])) {
            clearAll(source, args.length > 2 && "confirm".equalsIgnoreCase(args[2]));
            return;
        }
        String ip = parseIp(source, args[1]);
        if (ip == null) {
            return;
        }
        boolean removed = plugin.guard().clear(ip);
        source.sendMessage(removed
                ? GatekeepTexts.MSG.success("Cleared " + ip + ".")
                : GatekeepTexts.MSG.info(ip + " was not tracked."));
    }

    private void clearAll(CommandSource source, boolean consoleConfirmed) {
        int tracked = plugin.guard().trackedCount();
        if (source instanceof Player player) {
            plugin.confirm().purgeExpired();
            ConfirmChat.Prompt prompt = plugin.confirm().ask(
                    player.getUniqueId(),
                    "Clear all " + tracked + " tracked addresses?",
                    "gatekeep",
                    () -> player.sendMessage(GatekeepTexts.MSG.success(
                            "Cleared " + plugin.guard().clearAll() + " addresses.")),
                    () -> player.sendMessage(GatekeepTexts.MSG.info("Cancelled.")),
                    () -> player.sendMessage(GatekeepTexts.MSG.info("Confirmation expired.")));
            player.sendMessage(prompt.message());
            return;
        }
        if (!consoleConfirmed) {
            source.sendMessage(GatekeepTexts.MSG.warn(
                    "This forgets " + tracked + " addresses. Run /gatekeep clear * confirm to proceed."));
            return;
        }
        source.sendMessage(GatekeepTexts.MSG.success("Cleared " + plugin.guard().clearAll() + " addresses."));
    }

    private void resolve(CommandSource source, String[] args, boolean accepted) {
        if (!(source instanceof Player player) || args.length < 2) {
            source.sendMessage(GatekeepTexts.MSG.warn("Use the Confirm or Cancel button from /gatekeep clear *."));
            return;
        }
        try {
            if (!plugin.confirm().resolve(player.getUniqueId(), args[1], accepted)) {
                source.sendMessage(GatekeepTexts.MSG.warn("That confirmation is no longer valid."));
            }
        } catch (IllegalArgumentException exception) {
            source.sendMessage(GatekeepTexts.MSG.error("That confirmation belongs to someone else."));
        }
    }

    private void reload(CommandSource source) {
        try {
            plugin.reload();
            source.sendMessage(GatekeepTexts.MSG.success("Reloaded gatekeep config."));
        } catch (RuntimeException exception) {
            source.sendMessage(GatekeepTexts.MSG.error(
                    "Reload failed, keeping previous settings: " + exception.getMessage()));
        }
    }

    private String parseIp(CommandSource source, String raw) {
        try {
            Cidr.address(raw);
            return raw.trim();
        } catch (IllegalArgumentException exception) {
            source.sendMessage(GatekeepTexts.MSG.error("'" + raw + "' is not an IP address."));
            return null;
        }
    }

    private Component help(CommandSource source) {
        return HelpBuilder.root("gatekeep")
                .entry("/gatekeep status", "Tracked addresses and recent throttles", ADMIN)
                .entry("/gatekeep check <ip>", "Attempts, retry time and support code", ADMIN)
                .entry("/gatekeep clear <ip>", "Forget one address", ADMIN)
                .entry("/gatekeep clear *", "Forget every tracked address (asks first)", ADMIN)
                .entry("/gatekeep reload", "Reload config.yml", ADMIN)
                .build(source::hasPermission, 1, 20);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length > 1 && !invocation.source().hasPermission(ADMIN)) {
            return List.of();
        }
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("help", "status", "check", "clear", "reload").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && "clear".equalsIgnoreCase(args[0])) {
            return java.util.stream.Stream.concat(
                            java.util.stream.Stream.of("*"),
                            plugin.guard().snapshots(STATUS_LIMIT).stream().map(ConnectionGuard.Entry::key))
                    .filter(option -> option.startsWith(args[1]))
                    .toList();
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(ADMIN);
    }
}
