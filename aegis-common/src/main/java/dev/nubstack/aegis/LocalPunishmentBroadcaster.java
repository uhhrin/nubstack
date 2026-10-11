package dev.nubstack.aegis;

/**
 * In-process broadcaster for single-node tests and degraded mode.
 */
public final class LocalPunishmentBroadcaster implements PunishmentBroadcaster {

    @Override
    public void publish(PunishmentEvent event) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }
}
