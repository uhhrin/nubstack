package dev.nubstack.spine.redis;

import io.lettuce.core.RedisURI;

/**
 * Redis connection settings for cross-server messaging.
 */
public record RedisSettings(String host, int port, String password) {

    public RedisSettings {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        host = host.trim();
        if (password != null && password.isBlank()) {
            password = null;
        }
    }

    public RedisURI toUri() {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(host)
                .withPort(port);
        if (password != null) {
            builder.withPassword(password.toCharArray());
        }
        return builder.build();
    }
}
