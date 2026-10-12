package dev.nubstack.cairn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.nubstack.cairn.grave.AccessMode;
import dev.nubstack.cairn.grave.XpPolicy;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class CairnConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_SEARCH_RADIUS = 16;

    private int expireMinutes = 15;
    private int maxGravesPerPlayer = 3;
    private AccessMode accessMode = AccessMode.OWNER;
    private XpPolicy xpPolicy = XpPolicy.DROP;
    private int searchRadius = 6;

    public int expireMinutes() {
        return expireMinutes;
    }

    public int expireTicks() {
        return expireMinutes * 20 * 60;
    }

    public int maxGravesPerPlayer() {
        return maxGravesPerPlayer;
    }

    public AccessMode accessMode() {
        return accessMode;
    }

    public XpPolicy xpPolicy() {
        return xpPolicy;
    }

    public int searchRadius() {
        return searchRadius;
    }

    public static CairnConfig loadOrCreate(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            CairnConfig defaults = new CairnConfig();
            defaults.save(path);
            return defaults;
        }
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(path)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("cairn config is not valid JSON: " + path, e);
        }
        CairnConfig config = new CairnConfig();
        config.expireMinutes = Math.max(1, readInt(json, "expireMinutes", config.expireMinutes));
        config.maxGravesPerPlayer = Math.max(1, readInt(json, "maxGravesPerPlayer", config.maxGravesPerPlayer));
        config.searchRadius = Math.min(MAX_SEARCH_RADIUS, Math.max(0, readInt(json, "searchRadius", config.searchRadius)));
        if (json.has("accessMode")) {
            config.accessMode = AccessMode.parse(json.get("accessMode").getAsString());
        }
        if (json.has("xpPolicy")) {
            config.xpPolicy = XpPolicy.parse(json.get("xpPolicy").getAsString());
        }
        return config;
    }

    private static int readInt(JsonObject json, String key, int fallback) throws IOException {
        JsonElement element = json.get(key);
        if (element == null) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException e) {
            throw new IOException("cairn config key " + key + " must be a number", e);
        }
    }

    public void save(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Files.createDirectories(path.toAbsolutePath().getParent());
        JsonObject json = new JsonObject();
        json.addProperty("expireMinutes", expireMinutes);
        json.addProperty("maxGravesPerPlayer", maxGravesPerPlayer);
        json.addProperty("accessMode", accessMode.configName());
        json.addProperty("xpPolicy", xpPolicy.configName());
        json.addProperty("searchRadius", searchRadius);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(json, writer);
        }
    }
}
