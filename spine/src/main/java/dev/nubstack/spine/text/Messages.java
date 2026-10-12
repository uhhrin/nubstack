package dev.nubstack.spine.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * MiniMessage helpers and the portfolio chat kit entry points.
 */
public final class Messages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Messages() {
    }

    public static Component parse(String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) {
            throw new IllegalArgumentException("miniMessage must not be blank");
        }
        return MINI_MESSAGE.deserialize(miniMessage);
    }

    public static String plain(String miniMessage) {
        return PLAIN.serialize(parse(miniMessage));
    }

    /**
     * Starts a prefixed fluent builder: {@code Messages.prefixed("tether").info("...")}.
     */
    public static PrefixedMessages prefixed(String pluginName) {
        return new PrefixedMessages(pluginName);
    }
}

