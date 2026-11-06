package br.pedro.pickuprange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PickupRanges {
    // This slider position delegates pickup to the game rather than setting a radius.
    static final int DEFAULT = 7;
    static final int MAX = 32;
    private final Path file;
    private final ConcurrentHashMap<UUID, Integer> ranges = new ConcurrentHashMap<>();

    PickupRanges(Path file) {
        this.file = file;
        if (Files.notExists(file)) {
            return;
        }
        try (var in = Files.newInputStream(file)) {
            var saved = new Properties();
            saved.load(in);
            for (String key : saved.stringPropertyNames()) {
                int steps = Integer.parseInt(saved.getProperty(key));
                if (steps > DEFAULT && steps <= MAX) {
                    ranges.put(UUID.fromString(key), steps);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not load " + file, e);
        }
    }

    int get(UUID player) {
        return ranges.getOrDefault(player, DEFAULT);
    }

    static float blocks(int steps) {
        return steps / 4.0f;
    }

    synchronized void save(UUID player, int steps) throws IOException {
        if (get(player) == steps) {
            return;
        }
        var saved = new Properties();
        ranges.forEach((id, range) -> saved.setProperty(id.toString(), range.toString()));
        if (steps == DEFAULT) {
            saved.remove(player.toString());
        } else {
            saved.setProperty(player.toString(), Integer.toString(steps));
        }

        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "ranges-", ".tmp");
        try {
            try (var out = Files.newOutputStream(temporary)) {
                saved.store(out, "Pickup ranges in quarter-block steps");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        if (steps == DEFAULT) {
            ranges.remove(player);
        } else {
            ranges.put(player, steps);
        }
    }
}
