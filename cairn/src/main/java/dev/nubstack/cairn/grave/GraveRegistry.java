package dev.nubstack.cairn.grave;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * In-memory grave index keyed by dimension + block position.
 */
public final class GraveRegistry {

    private final Map<String, GraveRecord> byId = new LinkedHashMap<>();
    private final Map<GraveKey, String> byLocation = new LinkedHashMap<>();

    public GraveRecord add(GraveRecord record) {
        Objects.requireNonNull(record, "record");
        if (byId.containsKey(record.id())) {
            throw new IllegalArgumentException("duplicate grave id: " + record.id());
        }
        GraveKey key = record.key();
        if (byLocation.containsKey(key)) {
            throw new IllegalArgumentException("location already occupied: " + key);
        }
        byId.put(record.id(), record);
        byLocation.put(key, record.id());
        return record;
    }

    public Optional<GraveRecord> find(GraveKey key) {
        Objects.requireNonNull(key, "key");
        String id = byLocation.get(key);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<GraveRecord> findById(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<GraveRecord> findByEntity(UUID entityId) {
        Objects.requireNonNull(entityId, "entityId");
        for (GraveRecord grave : byId.values()) {
            if (entityId.equals(grave.interactionId()) || entityId.equals(grave.displayId())) {
                return Optional.of(grave);
            }
        }
        return Optional.empty();
    }

    /** Replaces an existing grave by id (location may stay the same). */
    public void replace(GraveRecord record) {
        Objects.requireNonNull(record, "record");
        GraveRecord previous = byId.get(record.id());
        if (previous == null) {
            throw new IllegalArgumentException("unknown grave id: " + record.id());
        }
        byLocation.remove(previous.key());
        byId.put(record.id(), record);
        byLocation.put(record.key(), record.id());
    }

    public List<GraveRecord> listFor(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return byId.values().stream().filter(grave -> grave.ownerId().equals(ownerId)).toList();
    }

    public List<GraveRecord> listForName(String ownerName) {
        Objects.requireNonNull(ownerName, "ownerName");
        return byId.values().stream().filter(grave -> grave.ownerName().equalsIgnoreCase(ownerName)).toList();
    }

    public String nextId() {
        long max = 0;
        for (String id : byId.keySet()) {
            try {
                max = Math.max(max, Long.parseLong(id));
            } catch (NumberFormatException ignored) {
                // non-numeric ids from hand edited files do not affect numbering
            }
        }
        return Long.toString(max + 1);
    }

    /** Marks a grave so the next dimension tick expires it. */
    public boolean expire(String id) {
        Objects.requireNonNull(id, "id");
        GraveRecord grave = byId.get(id);
        if (grave == null) {
            return false;
        }
        byId.put(id, grave.withTicks(0));
        return true;
    }

    public Collection<GraveRecord> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public List<GraveRecord> tickDimension(String dimension, int ticks) {
        Objects.requireNonNull(dimension, "dimension");
        if (ticks <= 0) {
            return List.of();
        }
        List<GraveRecord> expired = new ArrayList<>();
        List<GraveRecord> updated = new ArrayList<>();
        for (GraveRecord grave : byId.values()) {
            if (!grave.dimension().equals(dimension) || grave.isDespawning()) {
                continue;
            }
            int left = grave.ticksRemaining() - ticks;
            if (left <= 0) {
                expired.add(grave);
            } else {
                updated.add(grave.withTicks(left));
            }
        }
        for (GraveRecord grave : expired) {
            remove(grave.id());
        }
        for (GraveRecord grave : updated) {
            byId.put(grave.id(), grave);
        }
        return List.copyOf(expired);
    }

    public void enforceMaxGraves(UUID ownerId, int max, Consumer<GraveRecord> onEvict) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(onEvict, "onEvict");
        if (max <= 0) {
            return;
        }
        List<GraveRecord> owned = new ArrayList<>(listFor(ownerId));
        while (owned.size() > max) {
            GraveRecord oldest = owned.removeFirst();
            remove(oldest.id());
            onEvict.accept(oldest);
        }
    }

    public boolean remove(String id) {
        Objects.requireNonNull(id, "id");
        GraveRecord removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        byLocation.remove(removed.key());
        return true;
    }

    public List<GraveRecord> snapshot() {
        return List.copyOf(byId.values());
    }

    public void load(List<GraveRecord> records) {
        Objects.requireNonNull(records, "records");
        byId.clear();
        byLocation.clear();
        for (GraveRecord record : records) {
            add(record);
        }
    }

    public void clear() {
        byId.clear();
        byLocation.clear();
    }
}
