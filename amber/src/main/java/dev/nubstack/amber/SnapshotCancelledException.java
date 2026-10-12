package dev.nubstack.amber;

import java.io.IOException;

public final class SnapshotCancelledException extends IOException {

    public SnapshotCancelledException() {
        super("Snapshot cancelled");
    }
}
