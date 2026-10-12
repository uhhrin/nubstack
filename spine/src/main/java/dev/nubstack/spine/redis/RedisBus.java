package dev.nubstack.spine.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Lightweight Redis pub/sub helper. Connections are opened on first use.
 * Callers must {@link #close()} on shutdown.
 */
public final class RedisBus implements AutoCloseable {

    private final RedisClient client;
    private final Object lock = new Object();
    private final ConcurrentMap<String, Consumer<String>> handlers = new ConcurrentHashMap<>();
    private StatefulRedisPubSubConnection<String, String> publishConnection;
    private StatefulRedisPubSubConnection<String, String> subscribeConnection;
    private volatile boolean closed;

    private RedisBus(RedisClient client) {
        this.client = client;
    }

    /**
     * Creates a bus for the given settings without contacting Redis until a command runs.
     */
    public static RedisBus open(RedisSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new RedisBus(RedisClient.create(settings.toUri()));
    }

    public void publish(String channel, String message) {
        ensureOpen();
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(message, "message");
        if (channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        publishConnection().sync().publish(channel, message);
    }

    public AutoCloseable subscribe(String channel, Consumer<String> handler) {
        ensureOpen();
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(handler, "handler");
        if (channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        if (handlers.putIfAbsent(channel, handler) != null) {
            throw new IllegalStateException("Already subscribed to channel: " + channel);
        }
        subscribeConnection().sync().subscribe(channel);
        return () -> unsubscribe(channel);
    }

    private StatefulRedisPubSubConnection<String, String> publishConnection() {
        StatefulRedisPubSubConnection<String, String> existing = publishConnection;
        if (existing != null) {
            return existing;
        }
        synchronized (lock) {
            if (publishConnection == null) {
                publishConnection = client.connectPubSub();
            }
            return publishConnection;
        }
    }

    private StatefulRedisPubSubConnection<String, String> subscribeConnection() {
        StatefulRedisPubSubConnection<String, String> existing = subscribeConnection;
        if (existing != null) {
            return existing;
        }
        synchronized (lock) {
            if (subscribeConnection == null) {
                subscribeConnection = client.connectPubSub();
                subscribeConnection.addListener(new RedisPubSubAdapter<>() {
                    @Override
                    public void message(String channel, String message) {
                        Consumer<String> registered = handlers.get(channel);
                        if (registered != null) {
                            registered.accept(message);
                        }
                    }
                });
            }
            return subscribeConnection;
        }
    }

    private void unsubscribe(String channel) {
        handlers.remove(channel);
        if (!closed && subscribeConnection != null) {
            subscribeConnection.sync().unsubscribe(channel);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("RedisBus is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        handlers.clear();
        synchronized (lock) {
            if (publishConnection != null) {
                publishConnection.close();
                publishConnection = null;
            }
            if (subscribeConnection != null) {
                subscribeConnection.close();
                subscribeConnection = null;
            }
        }
        client.shutdown();
    }
}
