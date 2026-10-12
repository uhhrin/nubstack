package dev.nubstack.cairn.grave;

import java.util.Objects;

public record GraveKey(String dimension, int x, int y, int z) {

    public GraveKey {
        Objects.requireNonNull(dimension, "dimension");
    }
}
