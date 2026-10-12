package dev.nubstack.cairn.grave;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class GraveMessages {

    private static final int TICKS_PER_SECOND = 20;

    private GraveMessages() {
    }

    public static MutableComponent copyCoordsButton(int x, int y, int z) {
        String payload = x + " " + y + " " + z;
        return Component.literal("[Copy coords]")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.CopyToClipboard(payload))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy"))));
    }

    public static MutableComponent tpButton(String graveId) {
        return Component.literal("[Tp]")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/cairn admin tp " + graveId))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to teleport to this grave"))));
    }

    public static Component deathMessage(String graveId, String dimension, int x, int y, int z, boolean includeTp) {
        MutableComponent message = Component.literal("Your grave was placed at ")
                .append(Component.literal(x + " " + y + " " + z).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" in "))
                .append(Component.literal(dimension).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" "))
                .append(copyCoordsButton(x, y, z));
        if (includeTp) {
            message.append(Component.literal(" ")).append(tpButton(graveId));
        }
        return message;
    }

    public static Component expiredWhisper() {
        return Component.literal("Grave expired; items dropped");
    }

    public static String timeLeft(int ticks) {
        int seconds = Math.max(0, ticks) / TICKS_PER_SECOND;
        return seconds >= 60 ? (seconds / 60) + "m" : seconds + "s";
    }

    public static Component graveLine(GraveRecord grave, boolean admin) {
        MutableComponent line = Component.literal("#" + grave.id() + " ").withStyle(ChatFormatting.GRAY);
        if (admin) {
            line.append(Component.literal(grave.ownerName() + " ").withStyle(ChatFormatting.WHITE));
        }
        line.append(Component.literal(grave.x() + " " + grave.y() + " " + grave.z()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" in " + grave.dimension() + ", " + timeLeft(grave.ticksRemaining()) + " left "))
                .append(copyCoordsButton(grave.x(), grave.y(), grave.z()));
        if (admin) {
            line.append(Component.literal(" ")).append(tpButton(grave.id()));
        }
        return line;
    }

    public static Component helpText() {
        return Component.literal("cairn: /cairn locate | /cairn list | admin list [player] | admin tp <id>"
                + " | admin clear <id|expired|all> | admin expire <id>");
    }
}
