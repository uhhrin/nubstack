package dev.nubstack.tether;

import dev.nubstack.spine.text.ConfirmChat;
import dev.nubstack.spine.text.Durations;
import dev.nubstack.spine.text.HelpBuilder;
import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * {@code /tether} (aliases {@code /link}, {@code /discord}). Database work runs off the main thread.
 */
final class TetherCommand implements TabExecutor {

    private static final String LINK = "tether.link";
    private static final String LOOKUP = "tether.admin.lookup";
    private static final String UNLINK = "tether.admin.unlink";
    private static final String CODES = "tether.admin.codes";
    private static final String RELOAD = "tether.admin.reload";
    private static final int HELP_PAGE_SIZE = 8;
    private static final List<String> TOP_LEVEL = List.of("help", "link", "cancel", "status", "unlink", "admin", "reload");

    private final TetherPlugin plugin;
    private final PrefixedMessages msg = Messages.prefixed("tether");
    private final ConfirmChat confirm = new ConfirmChat();

    TetherCommand(TetherPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] rawArgs) {
        String[] args = normalize(label, rawArgs);
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> help(sender, 1, true);
            case "help" -> help(sender, args.length > 1 ? parsePage(args[1]) : 1, false);
            case "link" -> link(sender, args.length > 1 && args[1].equalsIgnoreCase("renew"));
            case "cancel" -> {
                if (args.length > 1) {
                    resolveConfirm(sender, args[1], false);
                } else {
                    cancel(sender);
                }
            }
            case "confirm" -> {
                if (args.length > 1) {
                    resolveConfirm(sender, args[1], true);
                } else {
                    sender.sendMessage(msg.error("Usage: /tether confirm <token>"));
                }
            }
            case "status" -> status(sender);
            case "unlink" -> unlink(sender);
            case "admin" -> admin(sender, args);
            case "reload" -> reload(sender);
            default -> sender.sendMessage(msg.error("Unknown subcommand. Try /tether help"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] rawArgs) {
        String[] args = normalize(label, rawArgs);
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("lookup") || args[1].equalsIgnoreCase("unlink"))
                && sender.hasPermission(args[1].equalsIgnoreCase("lookup") ? LOOKUP : UNLINK)) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return complete(args, sender::hasPermission);
    }

    /** Maps the {@code /link} and {@code /discord} aliases onto subcommands. */
    static String[] normalize(String label, String[] args) {
        String name = label.toLowerCase(Locale.ROOT);
        if (name.equals("link")) {
            String[] out = new String[args.length + 1];
            out[0] = "link";
            System.arraycopy(args, 0, out, 1, args.length);
            return out;
        }
        if (name.equals("discord")) {
            return new String[] {"status"};
        }
        return args;
    }

    static boolean looksLikeDiscordId(String raw) {
        return raw.length() >= 17 && raw.length() <= 20 && raw.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    static List<String> complete(String[] args, Predicate<String> has) {
        if (args.length == 0) {
            return List.of();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (String top : TOP_LEVEL) {
                if (visible(top, has)) {
                    options.add(top);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("link") && has.test(LINK)) {
            options.add("renew");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (has.test(LOOKUP)) {
                options.add("lookup");
            }
            if (has.test(UNLINK)) {
                options.add("unlink");
            }
            if (has.test(CODES)) {
                options.add("codes");
            }
        }
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    private static boolean visible(String top, Predicate<String> has) {
        return switch (top) {
            case "help" -> true;
            case "link", "cancel", "status", "unlink" -> has.test(LINK);
            case "admin" -> has.test(LOOKUP) || has.test(UNLINK) || has.test(CODES);
            case "reload" -> has.test(RELOAD);
            default -> false;
        };
    }

    private void help(CommandSender sender, int page, boolean withStatus) {
        HelpBuilder help = HelpBuilder.root("tether")
                .entry("/tether help", "Show this list", null)
                .entry("/tether link", "Get a code to link Discord", LINK)
                .entry("/tether link renew", "Replace your code with a new one", LINK)
                .entry("/tether cancel", "Cancel your pending code", LINK)
                .entry("/tether status", "Show your link and copy your Discord id", LINK)
                .entry("/tether unlink", "Unlink your Discord account", LINK)
                .entry("/tether admin lookup <player|discordId>", "Show a link row", LOOKUP)
                .entry("/tether admin unlink <player>", "Force unlink a player", UNLINK)
                .entry("/tether admin codes", "List pending codes", CODES)
                .entry("/tether reload", "Reload config.yml (not database settings)", RELOAD);
        sender.sendMessage(help.build(sender::hasPermission, page, HELP_PAGE_SIZE));
        if (!withStatus) {
            return;
        }
        sender.sendMessage(msg.info("Discord bridge: " + plugin.bridgeStatus()));
        if (sender instanceof Player player && player.hasPermission(LINK)) {
            plugin.db(sender, () -> {
                Optional<LinkService.LinkRow> row = plugin.links().linkByPlayer(player.getUniqueId());
                sender.sendMessage(row
                        .map(r -> msg.success("You are linked as @" + tagOf(r)))
                        .orElseGet(() -> msg.warn("You are not linked. Run /tether link")));
            });
        }
    }

    private void link(CommandSender sender, boolean renew) {
        Player player = requirePlayer(sender, LINK);
        if (player != null) {
            plugin.issueCard(player, renew, false);
        }
    }

    private void cancel(CommandSender sender) {
        Player player = requirePlayer(sender, LINK);
        if (player == null) {
            return;
        }
        plugin.db(sender, () -> {
            boolean removed = plugin.links().cancelCode(player.getUniqueId());
            plugin.countdown().stop(player.getUniqueId());
            sender.sendMessage(removed ? msg.success("Link code cancelled.") : msg.info("You have no pending code."));
        });
    }

    private void status(CommandSender sender) {
        Player player = requirePlayer(sender, LINK);
        if (player == null) {
            return;
        }
        plugin.db(sender, () -> {
            LinkService links = plugin.links();
            Optional<LinkService.LinkRow> row = links.linkByPlayer(player.getUniqueId());
            if (row.isPresent()) {
                LinkService.LinkRow r = row.get();
                sender.sendMessage(msg.success("Linked as @" + tagOf(r))
                        .append(Component.space())
                        .append(msg.copyButton("Copy Discord id", r.discordId())));
                if (r.linkedAt().getEpochSecond() > 0) {
                    Duration ago = Duration.between(r.linkedAt(), Instant.now());
                    sender.sendMessage(msg.info("Linked " + Durations.remaining(ago) + " ago"));
                }
            } else {
                Optional<LinkService.PendingCode> pending = links.pendingCode(player.getUniqueId());
                if (pending.isPresent()) {
                    long left = pending.get().remainingSeconds(Clock.systemUTC());
                    sender.sendMessage(msg.warn("Not linked. A code is pending for "
                            + Durations.remaining(Duration.ofSeconds(left)) + ". Run /tether link to see it."));
                } else {
                    sender.sendMessage(msg.warn("Not linked. Run /tether link"));
                }
            }
            sender.sendMessage(msg.info("Discord bridge: " + plugin.bridgeStatus()));
        });
    }

    private void unlink(CommandSender sender) {
        Player player = requirePlayer(sender, LINK);
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        plugin.db(sender, () -> {
            Optional<LinkService.LinkRow> row = plugin.links().linkByPlayer(id);
            if (row.isEmpty()) {
                sender.sendMessage(msg.warn("You are not linked."));
                return;
            }
            ConfirmChat.Prompt prompt = confirm.ask(
                    id,
                    "Unlink @" + tagOf(row.get()) + " from your account?",
                    "tether",
                    () -> plugin.db(sender, () -> {
                        boolean removed = plugin.links().unlink(id);
                        sender.sendMessage(removed ? msg.success("Unlinked.") : msg.info("You were not linked."));
                    }),
                    () -> sender.sendMessage(msg.info("Unlink cancelled.")));
            sender.sendMessage(prompt.message());
        });
    }

    private void resolveConfirm(CommandSender sender, String token, boolean accepted) {
        Player player = requirePlayer(sender, LINK);
        if (player == null) {
            return;
        }
        try {
            if (!confirm.resolve(player.getUniqueId(), token, accepted)) {
                sender.sendMessage(msg.warn("That confirmation expired or does not exist."));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(msg.error("That confirmation is not yours."));
        }
    }

    private void admin(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "lookup" -> {
                if (!allowed(sender, LOOKUP)) {
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(msg.error("Usage: /tether admin lookup <player|discordId>"));
                    return;
                }
                lookup(sender, args[2]);
            }
            case "unlink" -> {
                if (!allowed(sender, UNLINK)) {
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(msg.error("Usage: /tether admin unlink <player>"));
                    return;
                }
                adminUnlink(sender, args[2]);
            }
            case "codes" -> {
                if (allowed(sender, CODES)) {
                    codes(sender);
                }
            }
            default -> sender.sendMessage(msg.error("Usage: /tether admin <lookup|unlink|codes>"));
        }
    }

    private void lookup(CommandSender sender, String target) {
        if (looksLikeDiscordId(target)) {
            plugin.db(sender, () -> sender.sendMessage(describe(plugin.links().linkByDiscordId(target), target)));
            return;
        }
        UUID id = resolvePlayer(sender, target);
        if (id != null) {
            plugin.db(sender, () -> sender.sendMessage(describe(plugin.links().linkByPlayer(id), target)));
        }
    }

    private Component describe(Optional<LinkService.LinkRow> row, String asked) {
        if (row.isEmpty()) {
            return msg.warn("No link found for " + asked);
        }
        LinkService.LinkRow r = row.get();
        return msg.info(plugin.nameOf(r.playerId()) + " is linked as @" + tagOf(r))
                .append(Component.space())
                .append(msg.copyButton("Copy UUID", r.playerId().toString()))
                .append(Component.space())
                .append(msg.copyButton("Copy Discord id", r.discordId()));
    }

    private void adminUnlink(CommandSender sender, String target) {
        UUID id = resolvePlayer(sender, target);
        if (id == null) {
            return;
        }
        plugin.db(sender, () -> {
            boolean removed = plugin.links().unlink(id);
            plugin.getLogger().info(sender.getName() + " force-unlinked " + target + " (" + id + ")");
            sender.sendMessage(removed
                    ? msg.success("Unlinked " + target + ".")
                    : msg.info(target + " was not linked."));
        });
    }

    private void codes(CommandSender sender) {
        plugin.db(sender, () -> {
            LinkService links = plugin.links();
            List<LinkService.PendingCode> pending = links.listPendingCodes(20);
            sender.sendMessage(msg.info("Pending codes: " + links.pendingCodeCount()));
            for (LinkService.PendingCode code : pending) {
                long left = code.remainingSeconds(Clock.systemUTC());
                // codes are never shown: they would let staff link an account to their own Discord
                sender.sendMessage(msg.info(plugin.nameOf(code.playerId()) + " expires in "
                        + Durations.remaining(Duration.ofSeconds(left)) + ", attempts " + code.attempts()));
            }
        });
    }

    private void reload(CommandSender sender) {
        if (allowed(sender, RELOAD)) {
            plugin.reload(sender);
        }
    }

    /** Resolves a name from the profile cache only, so no Mojang lookup blocks the main thread. */
    private UUID resolvePlayer(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached == null) {
            sender.sendMessage(msg.warn("Unknown player " + name + " (they must have joined before)."));
            return null;
        }
        return cached.getUniqueId();
    }

    private Player requirePlayer(CommandSender sender, String permission) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.error("Players only."));
            return null;
        }
        return allowed(sender, permission) ? player : null;
    }

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(msg.error("You do not have permission."));
        return false;
    }

    private static int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String tagOf(LinkService.LinkRow row) {
        return row.discordTag() == null || row.discordTag().isBlank() ? row.discordId() : row.discordTag();
    }
}
