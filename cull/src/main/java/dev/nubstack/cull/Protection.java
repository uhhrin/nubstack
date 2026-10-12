package dev.nubstack.cull;

/** Entity traits that cleanup skips unless the operator turns the protection off. */
public enum Protection {
    NAMED("named"),
    TAMED("tamed"),
    LEASHED("leashed"),
    VILLAGERS("villagers"),
    ARMOR_STANDS("armor-stands"),
    VEHICLES("vehicles");

    private final String configKey;

    Protection(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
