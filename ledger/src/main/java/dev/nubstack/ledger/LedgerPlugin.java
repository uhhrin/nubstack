package dev.nubstack.ledger;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.config.ConfigFiles;
import dev.nubstack.spine.config.YamlConfig;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import dev.nubstack.spine.text.ConfirmChat;
import dev.nubstack.spine.text.HelpBuilder;
import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class LedgerPlugin extends JavaPlugin implements TabCompleter {

    private static final int HISTORY_PAGE_SIZE = 8;
    private static final int TOP_MAX = 50;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final PrefixedMessages msg = Messages.prefixed("ledger");
    // shortcut: expired confirmations stay in memory until resolved, upgrade with a periodic purgeExpired task
    private final ConfirmChat confirms = new ConfirmChat();

    private HikariDataSource dataSource;
    private EconomyService economy;
    private CurrencyFormat currency;
    private AmountParser amounts;
    private LedgerChat chat;
    private BigDecimal confirmAbove;

    @Override
    public void onEnable() {
        ConfigFiles.copyDefaultIfMissing(getDataFolder().toPath(), "config.yml", () -> getResource("config.yml"));
        YamlConfig config = YamlConfig.load(getDataFolder().toPath().resolve("config.yml"));
        loadSettings(config);
        dataSource = DataSources.create(new DatabaseSettings(
                config.requireString("database.jdbc-url"),
                config.requireString("database.username"),
                config.optionalString("database.password").orElse(""),
                "ledger",
                config.optionalInt("database.pool-size").orElse(5)));
        economy = new EconomyService(dataSource);
        economy.init();
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            Bukkit.getServicesManager().register(
                    Economy.class, new VaultEconomyHook(economy, currency), this, ServicePriority.Normal);
            getLogger().info("Registered Vault economy");
        }
        for (String command : List.of("bal", "pay", "ledger", "baltop")) {
            var cmd = getCommand(command);
            if (cmd != null) {
                cmd.setExecutor(this);
                cmd.setTabCompleter(this);
            }
        }
        getLogger().info("ledger enabled");
    }

    @Override
    public void onDisable() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private void loadSettings(YamlConfig config) {
        currency = new CurrencyFormat(config.optionalString("currency-name").orElse("Credits"));
        amounts = new AmountParser(new BigDecimal(config.optionalString("max-amount").orElse("1000000.00")));
        confirmAbove = new BigDecimal(config.optionalString("confirm-above").orElse("500.00"));
        chat = new LedgerChat(currency);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "bal" -> handleBal(sender, args);
            case "pay" -> handlePay(sender, args);
            case "baltop" -> handleTop(sender, args);
            default -> handleLedger(sender, args);
        };
    }

    private boolean handleBal(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length > 0) {
            Optional<OfflinePlayer> resolved = PlayerLookup.resolve(args[0]);
            if (resolved.isEmpty()) {
                sender.sendMessage(msg.error("Unknown player"));
                return true;
            }
            target = resolved.get();
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(msg.error("Specify a player"));
            return true;
        }
        UUID id = target.getUniqueId();
        String display = target.getName() == null ? id.toString() : target.getName();
        boolean self = sender instanceof Player p && p.getUniqueId().equals(id);
        boolean canViewHistory = self || canAudit(sender);
        async(sender, () -> economy.balance(id),
                bal -> sender.sendMessage(chat.balanceLine(display, bal, self, canViewHistory)));
        return true;
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player from)) {
            sender.sendMessage(msg.error("Players only"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(msg.info("/pay <player> <amount> [reason]"));
            return true;
        }
        Optional<Target> to = target(sender, args[0]);
        if (to.isEmpty()) {
            return true;
        }
        if (to.get().id().equals(from.getUniqueId())) {
            sender.sendMessage(msg.error("You cannot pay yourself"));
            return true;
        }
        Optional<BigDecimal> amount = amount(sender, args[1], false);
        if (amount.isEmpty()) {
            return true;
        }
        String reason = reason(args, 2, "player pay");
        Target dest = to.get();
        BigDecimal value = amount.get();
        if (value.compareTo(confirmAbove) >= 0) {
            ConfirmChat.Prompt prompt = confirms.ask(
                    from.getUniqueId(),
                    "Pay " + currency.format(value) + " to " + dest.name() + "?",
                    "ledger",
                    () -> runPay(from, dest, value, reason),
                    () -> from.sendMessage(msg.warn("Payment cancelled")),
                    () -> from.sendMessage(msg.warn("Payment confirmation expired")));
            from.sendMessage(chat.confirmPay(prompt.token(), value, dest.name()));
            return true;
        }
        runPay(from, dest, value, reason);
        return true;
    }

    private void runPay(Player from, Target to, BigDecimal amount, String reason) {
        UUID fromId = from.getUniqueId();
        async(from, () -> economy.transfer(fromId, to.id(), amount, reason), result -> {
            if (!result.success()) {
                from.sendMessage(chat.insufficientFunds(result.shortfall()));
                return;
            }
            from.sendMessage(chat.paySuccess(amount, to.name(), result.fromTxId()));
            Player online = Bukkit.getPlayer(to.id());
            if (online != null) {
                online.sendActionBar(chat.incomingPay(amount, from.getName()));
            }
        });
    }

    private boolean handleLedger(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(help(sender, 1));
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sender.sendMessage(help(sender, args.length > 1 ? parsePage(args[1]) : 1));
                yield true;
            }
            case "confirm" -> resolveConfirm(sender, args, true);
            case "cancel" -> resolveConfirm(sender, args, false);
            case "give" -> handleGive(sender, args);
            case "take" -> handleTake(sender, args);
            case "set" -> handleSet(sender, args);
            case "history" -> handleHistory(sender, args);
            case "receipt" -> handleReceipt(sender, args);
            case "top", "baltop" -> handleTop(sender, Arrays.copyOfRange(args, 1, args.length));
            case "reload" -> handleReload(sender);
            default -> {
                sender.sendMessage(msg.error("Unknown subcommand. Try /ledger help"));
                yield true;
            }
        };
    }

    private boolean resolveConfirm(CommandSender sender, String[] args, boolean accepted) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.error("Players only"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(msg.error("/ledger confirm|cancel <token>"));
            return true;
        }
        try {
            if (!confirms.resolve(player.getUniqueId(), args[1], accepted)) {
                sender.sendMessage(msg.warn("No pending confirmation"));
            }
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(msg.error(ex.getMessage()));
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(msg.info("/ledger give <player> <amount> [reason]"));
            return true;
        }
        Optional<Target> to = target(sender, args[1]);
        Optional<BigDecimal> amount = to.isEmpty() ? Optional.empty() : amount(sender, args[2], false);
        if (amount.isEmpty()) {
            return true;
        }
        String reason = reason(args, 3, "admin-give");
        async(sender, () -> economy.deposit(to.get().id(), amount.get(), reason),
                receipt -> sender.sendMessage(Component.text()
                        .append(msg.success("Gave " + currency.format(amount.get()) + " to " + to.get().name()))
                        .append(Component.space())
                        .append(msg.copyButton("Copy receipt", Long.toString(receipt)))
                        .build()));
        return true;
    }

    private boolean handleTake(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(msg.info("/ledger take <player> <amount> [reason]"));
            return true;
        }
        Optional<Target> to = target(sender, args[1]);
        Optional<BigDecimal> amount = to.isEmpty() ? Optional.empty() : amount(sender, args[2], false);
        if (amount.isEmpty()) {
            return true;
        }
        String reason = reason(args, 3, "admin-take");
        async(sender, () -> economy.withdraw(to.get().id(), amount.get(), reason), result -> {
            if (!result.success()) {
                sender.sendMessage(msg.error(to.get().name() + " has less than " + currency.format(amount.get())));
                return;
            }
            sender.sendMessage(Component.text()
                    .append(msg.success("Took " + currency.format(amount.get()) + " from " + to.get().name()))
                    .append(Component.space())
                    .append(msg.copyButton("Copy receipt", Long.toString(result.id())))
                    .build());
        });
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(msg.info("/ledger set <player> <amount> [reason]"));
            return true;
        }
        Optional<Target> to = target(sender, args[1]);
        Optional<BigDecimal> amount = to.isEmpty() ? Optional.empty() : amount(sender, args[2], true);
        if (amount.isEmpty()) {
            return true;
        }
        String reason = reason(args, 3, "admin-set");
        Target dest = to.get();
        BigDecimal value = amount.get();
        if (sender instanceof Player player) {
            ConfirmChat.Prompt prompt = confirms.ask(
                    player.getUniqueId(),
                    "Set " + dest.name() + " balance to " + currency.format(value) + "?",
                    "ledger",
                    () -> runSet(sender, dest, value, reason),
                    () -> sender.sendMessage(msg.warn("Set cancelled")),
                    () -> sender.sendMessage(msg.warn("Set confirmation expired")));
            sender.sendMessage(chat.confirmSet(prompt.token(), dest.name(), value));
            return true;
        }
        runSet(sender, dest, value, reason);
        return true;
    }

    private void runSet(CommandSender sender, Target to, BigDecimal amount, String reason) {
        async(sender, () -> economy.setBalance(to.id(), amount, reason),
                receipt -> sender.sendMessage(Component.text()
                        .append(msg.success("Set " + to.name() + " to " + currency.format(amount)))
                        .append(Component.space())
                        .append(msg.copyButton("Copy receipt", Long.toString(receipt)))
                        .build()));
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        int next = 1;
        Target target;
        if (args.length > next && !args[next].chars().allMatch(Character::isDigit)) {
            Optional<Target> named = target(sender, args[next]);
            if (named.isEmpty()) {
                return true;
            }
            target = named.get();
            next++;
            boolean self = sender instanceof Player p && p.getUniqueId().equals(target.id());
            if (!self && !canAudit(sender)) {
                sender.sendMessage(msg.error("No permission to view others' history"));
                return true;
            }
        } else if (sender instanceof Player player) {
            // shortcut: an all-digit argument is always a page, so a player literally named "123" cannot be looked up here
            target = new Target(player.getUniqueId(), player.getName());
        } else {
            sender.sendMessage(msg.error("Specify a player"));
            return true;
        }
        int page = args.length > next ? parsePage(args[next]) : 1;
        UUID id = target.id();
        String name = target.name();
        async(sender, () -> economy.history(id, page, HISTORY_PAGE_SIZE), rows -> {
            if (rows.isEmpty()) {
                sender.sendMessage(msg.info("No transactions for " + name + " on page " + page));
                return;
            }
            sender.sendMessage(msg.info("History for " + name + ", page " + page));
            for (EconomyService.TxRow row : rows) {
                sender.sendMessage(chat.historyLine(row.id(), row.delta(), row.reason()));
            }
        });
        return true;
    }

    private boolean handleReceipt(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg.info("/ledger receipt <id>"));
            return true;
        }
        long id;
        try {
            id = Long.parseLong(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(msg.error("Receipt id must be a number"));
            return true;
        }
        async(sender, () -> economy.receipt(id), row -> {
            boolean owner = row.isPresent() && sender instanceof Player p && p.getUniqueId().equals(row.get().playerId());
            // Same reply for missing and foreign receipts so ids cannot be probed.
            if (row.isEmpty() || !(owner || canAudit(sender))) {
                sender.sendMessage(msg.error("Receipt not found"));
                return;
            }
            EconomyService.TxRow tx = row.get();
            sender.sendMessage(chat.receiptLine(tx.id(), tx.delta(), tx.reason(), WHEN.format(tx.createdAt())));
        });
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        int limit = 10;
        if (args.length > 0) {
            try {
                limit = Math.min(TOP_MAX, Math.max(1, Integer.parseInt(args[0])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(msg.error("n must be a number"));
                return true;
            }
        }
        int n = limit;
        async(sender, () -> economy.top(n), rows -> {
            sender.sendMessage(msg.info("Top " + n + " balances"));
            int rank = 1;
            for (EconomyService.TopEntry entry : rows) {
                // getOfflinePlayer(UUID) reads the local cache and never does a network lookup.
                String name = Bukkit.getOfflinePlayer(entry.playerId()).getName();
                sender.sendMessage(msg.info(rank++ + ". " + (name == null ? entry.playerId() : name)
                        + " - " + currency.format(entry.balance())));
            }
        });
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        try {
            loadSettings(YamlConfig.load(getDataFolder().toPath().resolve("config.yml")));
            sender.sendMessage(msg.success("Reloaded ledger config (database settings need a restart)"));
        } catch (RuntimeException ex) {
            sender.sendMessage(msg.error("Reload failed, keeping old settings: " + ex.getMessage()));
        }
        return true;
    }

    private Component help(CommandSender sender, int page) {
        return HelpBuilder.root("ledger")
                .entry("/bal [player]", "Show balance", "ledger.use")
                .entry("/pay <player> <amount> [reason]", "Pay a player", "ledger.use")
                .entry("/baltop [n]", "Leaderboard", "ledger.use")
                .entry("/ledger history [player] [page]", "Transaction history", "ledger.use")
                .entry("/ledger receipt <id>", "Show one receipt", "ledger.use")
                .entry("/ledger give <player> <amount> [reason]", "Deposit", "ledger.admin")
                .entry("/ledger take <player> <amount> [reason]", "Withdraw", "ledger.admin")
                .entry("/ledger set <player> <amount> [reason]", "Set balance", "ledger.admin")
                .entry("/ledger reload", "Reload config", "ledger.admin")
                .build(sender::hasPermission, page, HISTORY_PAGE_SIZE);
    }

    /** Runs {@code work} off the main thread and replies on it; failures reach the sender, not just the console. */
    private <T> void async(CommandSender sender, Supplier<T> work, Consumer<T> done) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                T result = work.get();
                Bukkit.getScheduler().runTask(this, () -> done.accept(result));
            } catch (RuntimeException ex) {
                getLogger().warning("Ledger operation failed: " + ex);
                Bukkit.getScheduler().runTask(this, () ->
                        sender.sendMessage(msg.error("Something went wrong, see the console")));
            }
        });
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("ledger.admin")) {
            return true;
        }
        sender.sendMessage(msg.error("No permission"));
        return false;
    }

    private static boolean canAudit(CommandSender sender) {
        return sender.hasPermission("ledger.admin") || sender.hasPermission("ledger.audit");
    }

    private Optional<Target> target(CommandSender sender, String raw) {
        Optional<OfflinePlayer> resolved = PlayerLookup.resolve(raw);
        if (resolved.isEmpty()) {
            sender.sendMessage(msg.error("Unknown player: " + raw));
            return Optional.empty();
        }
        OfflinePlayer player = resolved.get();
        return Optional.of(new Target(player.getUniqueId(), player.getName() == null ? raw : player.getName()));
    }

    private Optional<BigDecimal> amount(CommandSender sender, String raw, boolean allowZero) {
        try {
            return Optional.of(allowZero ? amounts.parseNonNegative(raw) : amounts.parse(raw));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(msg.error(ex.getMessage()));
            return Optional.empty();
        }
    }

    private static String reason(String[] args, int from, String fallback) {
        return args.length > from ? String.join(" ", Arrays.copyOfRange(args, from, args.length)) : fallback;
    }

    private static int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private record Target(UUID id, String name) {
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("pay")) {
            return args.length == 1 ? onlineNames(args[0]) : args.length == 2 ? amountSuggestions(args[1]) : List.of();
        }
        if (name.equals("bal")) {
            return args.length == 1 ? onlineNames(args[0]) : List.of();
        }
        if (name.equals("baltop")) {
            return args.length == 1 ? filter(List.of("5", "10", "25", "50"), args[0]) : List.of();
        }
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("help", "history", "receipt", "top"));
            if (sender.hasPermission("ledger.admin")) {
                subs.addAll(List.of("give", "take", "set", "reload"));
            }
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("ledger.admin");
        if (args.length == 2 && (sub.equals("history") && canAudit(sender)
                || admin && List.of("give", "take", "set").contains(sub))) {
            return onlineNames(args[1]);
        }
        if (args.length == 2 && sub.equals("top")) {
            return filter(List.of("5", "10", "25", "50"), args[1]);
        }
        if (args.length == 3 && admin && List.of("give", "take", "set").contains(sub)) {
            return amountSuggestions(args[2]);
        }
        return List.of();
    }

    private static List<String> onlineNames(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                .sorted()
                .collect(Collectors.toList());
    }

    private static List<String> amountSuggestions(String prefix) {
        return filter(List.of("1", "10", "50", "100", "500", "1000"), prefix);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
