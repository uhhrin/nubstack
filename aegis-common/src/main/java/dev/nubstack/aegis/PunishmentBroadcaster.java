package dev.nubstack.aegis;

public interface PunishmentBroadcaster {

    String CHANNEL = "aegis:punishment";

    void publish(PunishmentEvent event);

    void close();
}
