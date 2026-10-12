package dev.nubstack.tether;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageRequest;

/**
 * JDA lifecycle, slash commands, inbound listener, and the bounded outbound queue.
 * Every outgoing message uses empty allowed mentions.
 */
final class DiscordBridge implements AutoCloseable {

    private static final int DISCORD_MESSAGE_LIMIT = 1900;
    private static final int GAME_MESSAGE_LIMIT = 256;
    private static final int DRAIN_PER_TICK = 4;

    private final TetherSettings settings;
    private final DiscordLinkHandler handler;
    private final BiConsumer<String, String> toGame;
    private final Logger logger;
    private final OutboundQueue queue;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> daemon(r, "tether-discord-worker"));
    private final ScheduledExecutorService drain =
            Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "tether-discord-drain"));
    private JDA jda;

    DiscordBridge(
            TetherSettings settings,
            DiscordLinkHandler handler,
            BiConsumer<String, String> toGame,
            Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.toGame = Objects.requireNonNull(toGame, "toGame");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.queue = new OutboundQueue(settings.outboundQueueCapacity(), logger);
    }

    void start() {
        MessageRequest.setDefaultMentions(EnumSet.noneOf(Message.MentionType.class));
        jda = JDABuilder.createLight(settings.discordToken(), GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new Listener())
                .build();
        // shortcut: fixed 1s drain with 4 sends per tick stays under Discord's per-channel limit,
        // make it configurable if a busy bridge needs more throughput
        drain.scheduleWithFixedDelay(this::drainQueue, 1, 1, TimeUnit.SECONDS);
    }

    String status() {
        return jda == null ? "starting" : jda.getStatus().name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Queues a Minecraft chat line for the bridge channel. */
    void sendChat(String playerName, String message) {
        if (settings.channelId().isBlank()) {
            return;
        }
        String line = DiscordText.truncate(DiscordText.formatOutbound(playerName, message), DISCORD_MESSAGE_LIMIT);
        queue.offer(new OutboundQueue.Job(settings.channelId(), line));
    }

    private void drainQueue() {
        JDA current = jda;
        if (current == null) {
            return;
        }
        queue.drain(DRAIN_PER_TICK, job -> {
            TextChannel channel = current.getTextChannelById(job.channelId());
            if (channel == null) {
                logger.warning("tether bridge channel " + job.channelId() + " not found; dropping message");
                return;
            }
            channel.sendMessage(job.content())
                    .setAllowedMentions(List.of())
                    .queue(null, failure -> logger.warning("tether Discord send failed: " + failure.getMessage()));
        });
    }

    @Override
    public void close() {
        drain.shutdownNow();
        worker.shutdown();
        JDA current = jda;
        if (current == null) {
            return;
        }
        current.shutdown();
        try {
            if (!current.awaitShutdown(Duration.ofSeconds(10))) {
                logger.warning("tether JDA did not shut down in 10s; forcing");
                current.shutdownNow();
                current.awaitShutdown(Duration.ofSeconds(5));
            }
        } catch (InterruptedException e) {
            current.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private final class Listener extends ListenerAdapter {

        @Override
        public void onReady(ReadyEvent event) {
            if (!settings.preferSlashCommands()) {
                return;
            }
            var commands = List.of(
                    Commands.slash("link", "Link your Minecraft account")
                            .addOption(OptionType.STRING, "code", "Code from /tether link in game", true),
                    Commands.slash("unlink", "Unlink your Minecraft account"));
            var guild = settings.guildId().isBlank() ? null : event.getJDA().getGuildById(settings.guildId());
            if (guild != null) {
                guild.updateCommands().addCommands(commands).queue(null, f -> logger.warning(
                        "tether slash command registration failed: " + f.getMessage()));
            } else {
                // shortcut: global commands can take up to an hour to appear, set discord.guild-id for instant
                event.getJDA().updateCommands().addCommands(commands).queue(null, f -> logger.warning(
                        "tether slash command registration failed: " + f.getMessage()));
            }
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            if (!settings.preferSlashCommands()) {
                return;
            }
            String name = event.getName();
            if (!name.equals("link") && !name.equals("unlink")) {
                return;
            }
            String code = event.getOption("code", mapping -> mapping.getAsString());
            String discordId = event.getUser().getId();
            String tag = event.getUser().getEffectiveName();
            event.deferReply(true).queue();
            worker.execute(() -> {
                String reply = run(() -> name.equals("link")
                        ? handler.link(code, discordId, tag)
                        : handler.unlink(discordId));
                event.getHook().sendMessage(reply).setAllowedMentions(List.of()).queue();
            });
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getAuthor().isBot() || !event.isFromGuild()) {
                return;
            }
            if (!event.getChannel().getId().equals(settings.channelId())) {
                return;
            }
            String content = event.getMessage().getContentDisplay();
            String discordId = event.getAuthor().getId();
            String tag = event.getAuthor().getEffectiveName();
            if (isCommand(content, "!link")) {
                String code = content.substring("!link".length()).trim();
                worker.execute(() -> reply(event, run(() -> handler.link(code, discordId, tag))));
            } else if (isCommand(content, "!unlink")) {
                worker.execute(() -> reply(event, run(() -> handler.unlink(discordId))));
            } else if (!content.isBlank()) {
                toGame.accept(tag, DiscordText.truncate(content, GAME_MESSAGE_LIMIT));
            }
        }

        private void reply(MessageReceivedEvent event, String text) {
            event.getChannel().sendMessage(text).setAllowedMentions(List.of())
                    .queue(null, f -> logger.warning("tether Discord reply failed: " + f.getMessage()));
        }

        private String run(java.util.function.Supplier<String> action) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "tether Discord command failed", e);
                return "Something went wrong. Try again later.";
            }
        }
    }

    static boolean isCommand(String content, String command) {
        if (!content.regionMatches(true, 0, command, 0, command.length())) {
            return false;
        }
        return content.length() == command.length() || Character.isWhitespace(content.charAt(command.length()));
    }
}
