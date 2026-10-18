package com.skensel.stopad;

import java.io.*;
import java.nio.file.*;

/** Validate first, fsync a sibling temporary file, then atomically replace the complete snapshot. */
final class BlocklistStore {
    interface Mover { void move(Path from, Path to) throws IOException; }
    private final Path directory, target;
    private final Mover mover;
    BlocklistStore(Path directory) {
        this(directory, (from, to) -> Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }
    BlocklistStore(Path directory, Mover mover) {
        this.directory = directory; this.target = directory.resolve("ads.snapshot"); this.mover = mover;
    }
    BlocklistSnapshot load() throws IOException {
        if (Files.size(target) > HostsBlocklist.MAX_BYTES + 1024) throw new IOException("Cached list too large");
        try (InputStream input = Files.newInputStream(target)) { return BlocklistSnapshot.decode(input); }
    }
    void cleanupStaging() throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "ads-update-*.tmp")) {
            for (Path entry : entries) Files.deleteIfExists(entry);
        }
    }
    void install(BlocklistSnapshot snapshot) throws IOException {
        Path temporary = Files.createTempFile(directory, "ads-update-", ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                snapshot.encode(output); output.getFD().sync();
            }
            mover.move(temporary, target);
        } finally { Files.deleteIfExists(temporary); }
    }
}
