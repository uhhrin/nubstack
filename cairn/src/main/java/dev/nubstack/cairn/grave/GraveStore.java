package dev.nubstack.cairn.grave;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JSON persistence for graves across restarts.
 *
 * shortcut: one small file rewritten on every change from the server thread,
 * upgrade to async or per-world SavedData if grave counts grow large.
 */
public final class GraveStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GraveStore() {
    }

    public static void save(Path file, List<GraveRecord> graves) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(graves, "graves");
        JsonArray array = new JsonArray();
        for (GraveRecord grave : graves) {
            JsonObject json = new JsonObject();
            json.addProperty("id", grave.id());
            json.addProperty("owner", grave.ownerId().toString());
            json.addProperty("ownerName", grave.ownerName());
            json.addProperty("dimension", grave.dimension());
            json.addProperty("x", grave.x());
            json.addProperty("y", grave.y());
            json.addProperty("z", grave.z());
            json.addProperty("ticksRemaining", grave.ticksRemaining());
            json.addProperty("despawnAnimTicks", grave.despawnAnimTicks());
            if (grave.interactionId() != null) {
                json.addProperty("interactionId", grave.interactionId().toString());
            }
            if (grave.displayId() != null) {
                json.addProperty("displayId", grave.displayId().toString());
            }
            JsonArray items = new JsonArray();
            for (String snbt : grave.itemsSnbt()) {
                items.add(snbt);
            }
            json.add("items", items);
            array.add(json);
        }
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp)) {
            GSON.toJson(array, writer);
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Reads graves. A missing file is empty. An unreadable file is renamed to
     * {@code <name>.corrupt} and treated as empty so a bad file never blocks startup.
     */
    public static List<GraveRecord> load(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (Files.notExists(file)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            List<GraveRecord> graves = new ArrayList<>();
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                JsonObject json = element.getAsJsonObject();
                List<String> items = new ArrayList<>();
                if (json.has("items") && json.get("items").isJsonArray()) {
                    for (JsonElement item : json.getAsJsonArray("items")) {
                        items.add(item.getAsString());
                    }
                }
                graves.add(new GraveRecord(
                        json.get("id").getAsString(),
                        UUID.fromString(json.get("owner").getAsString()),
                        json.get("ownerName").getAsString(),
                        json.get("dimension").getAsString(),
                        json.get("x").getAsInt(),
                        json.get("y").getAsInt(),
                        json.get("z").getAsInt(),
                        json.get("ticksRemaining").getAsInt(),
                        items,
                        readUuid(json, "interactionId"),
                        readUuid(json, "displayId"),
                        json.has("despawnAnimTicks") ? json.get("despawnAnimTicks").getAsInt() : 0));
            }
            return List.copyOf(graves);
        } catch (JsonParseException | IllegalStateException | NullPointerException | IllegalArgumentException
                | UnsupportedOperationException | ClassCastException e) {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
            return List.of();
        }
    }

    private static UUID readUuid(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        return UUID.fromString(json.get(key).getAsString());
    }
}
