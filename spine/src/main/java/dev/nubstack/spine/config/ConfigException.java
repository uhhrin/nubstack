package dev.nubstack.spine.config;

/**
 * Thrown when configuration cannot be loaded or a required value is invalid.
 */
public final class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
