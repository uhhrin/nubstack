package dev.nubstack.aegis;

import dev.nubstack.spine.redis.RedisBus;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RedisPunishmentBroadcaster implements PunishmentBroadcaster {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisPunishmentBroadcaster.class);

    private final RedisBus bus;
    private final AutoCloseable subscription;

    public RedisPunishmentBroadcaster(RedisBus bus, Consumer<PunishmentEvent> onEvent) {
        this.bus = Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(onEvent, "onEvent");
        this.subscription = bus.subscribe(CHANNEL, payload -> {
            try {
                onEvent.accept(PunishmentCodec.fromJson(payload));
            } catch (RuntimeException exception) {
                LOGGER.warn("Ignoring invalid aegis Redis payload", exception);
            }
        });
    }

    @Override
    public void publish(PunishmentEvent event) {
        try {
            bus.publish(CHANNEL, PunishmentCodec.toJson(event));
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to publish aegis punishment event", exception);
        }
    }

    @Override
    public void close() {
        try {
            subscription.close();
        } catch (Exception exception) {
            LOGGER.warn("Failed to unsubscribe aegis Redis channel", exception);
        }
        bus.close();
    }
}
