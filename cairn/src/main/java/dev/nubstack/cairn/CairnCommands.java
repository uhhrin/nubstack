package dev.nubstack.cairn;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nubstack.cairn.grave.GraveMessages;
import dev.nubstack.cairn.grave.GraveRecord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class CairnCommands {

    private final GraveManager graves;

    CairnCommands(GraveManager graves) {
        this.graves = graves;
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list")
                        .executes(ctx -> adminList(ctx, null))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> adminList(ctx, StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("tp")
                        .then(idArgument().executes(this::adminTp)))
                .then(Commands.literal("expire")
                        .then(idArgument().executes(this::adminExpire)))
                .then(Commands.literal("key")
                        .then(idArgument().executes(this::adminKey)))
                .then(Commands.literal("clear")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        withKeywords(), builder))
                                .executes(this::adminClear)));

        dispatcher.register(Commands.literal("cairn")
                .executes(ctx -> reply(ctx, GraveMessages.helpText()))
                .then(Commands.literal("locate").executes(this::locate))
                .then(Commands.literal("list").executes(this::list))
                .then(admin));
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> idArgument() {
        return Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ids(), builder));
    }

    private List<String> ids() {
        return graves.registry().all().stream().map(GraveRecord::id).toList();
    }

    private List<String> withKeywords() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(List.of("expired", "all"));
        out.addAll(ids());
        return out;
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private int locate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<GraveRecord> owned = graves.registry().listFor(player.getUUID());
        if (owned.isEmpty()) {
            return reply(ctx, Component.literal("You have no active graves"));
        }
        return reply(ctx, GraveMessages.graveLine(owned.getLast(), false));
    }

    private int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<GraveRecord> owned = graves.registry().listFor(player.getUUID());
        if (owned.isEmpty()) {
            return reply(ctx, Component.literal("You have no active graves"));
        }
        owned.forEach(grave -> reply(ctx, GraveMessages.graveLine(grave, false)));
        return owned.size();
    }

    private int adminList(CommandContext<CommandSourceStack> ctx, String playerName) {
        List<GraveRecord> found = playerName == null
                ? List.copyOf(graves.registry().all())
                : graves.registry().listForName(playerName);
        if (found.isEmpty()) {
            return reply(ctx, Component.literal("No graves found"));
        }
        found.forEach(grave -> reply(ctx, GraveMessages.graveLine(grave, true)));
        return found.size();
    }

    private int adminTp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<GraveRecord> grave = graves.registry().findById(StringArgumentType.getString(ctx, "id"));
        if (grave.isEmpty()) {
            return fail(ctx, "No grave with that id");
        }
        GraveRecord target = grave.get();
        ServerLevel level = ctx.getSource().getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(target.dimension())));
        if (level == null) {
            return fail(ctx, "That grave's dimension is not loaded: " + target.dimension());
        }
        // shortcut: stands one block above the grave without checking for headroom
        player.teleportTo(level, target.x() + 0.5, target.y() + 1.0, target.z() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), true);
        return reply(ctx, Component.literal("Teleported to grave #" + target.id()));
    }

    private int adminExpire(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        if (!graves.registry().expire(id)) {
            return fail(ctx, "No grave with that id");
        }
        return reply(ctx, Component.literal("Grave #" + id + " expires on the next tick"));
    }

    private int adminKey(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");
        ItemStack key = graves.reissueKey(id);
        if (key.isEmpty()) {
            return fail(ctx, "No grave with that id");
        }
        if (!player.getInventory().add(key)) {
            player.drop(key, false);
        }
        return reply(ctx, Component.literal("Reissued key for grave #" + id));
    }

    private int adminClear(CommandContext<CommandSourceStack> ctx) {
        String target = StringArgumentType.getString(ctx, "target");
        var server = ctx.getSource().getServer();
        if (target.equals("expired")) {
            int removed = graves.clearStale(server);
            return reply(ctx, Component.literal("Forgot " + removed + " grave(s) with no marker left"));
        }
        List<GraveRecord> targets;
        if (target.equals("all")) {
            targets = List.copyOf(graves.registry().all());
        } else {
            targets = graves.registry().findById(target).map(List::of).orElse(List.of());
            if (targets.isEmpty()) {
                return fail(ctx, "No grave with that id");
            }
        }
        for (GraveRecord grave : targets) {
            graves.registry().remove(grave.id());
            graves.destroyNow(server, grave);
        }
        graves.save();
        return reply(ctx, Component.literal("Cleared " + targets.size() + " grave(s); items dropped"));
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}
