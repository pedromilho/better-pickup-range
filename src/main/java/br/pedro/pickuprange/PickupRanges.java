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
    record Rules(boolean individual, boolean adminsOnly, int universalSteps) {}

    private final Path file;
    private final ConcurrentHashMap<UUID, Integer> ranges = new ConcurrentHashMap<>();
    private volatile Rules rules = new Rules(false, false, DEFAULT);

    PickupRanges(Path file) {
        this.file = file;
        if (Files.notExists(file)) {
            return;
        }
        try (var in = Files.newInputStream(file)) {
            var saved = new Properties();
            saved.load(in);
            int universalSteps = Integer.parseInt(saved.getProperty("universalSteps", Integer.toString(DEFAULT)));
            rules = new Rules(Boolean.parseBoolean(saved.getProperty("individual", "false")),
                Boolean.parseBoolean(saved.getProperty("adminsOnly", "false")),
                Math.clamp(universalSteps, DEFAULT, MAX));
            for (String key : saved.stringPropertyNames()) {
                if (key.equals("individual") || key.equals("adminsOnly") || key.equals("universalSteps")) {
                    continue;
                }
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

    Rules rules() {
        return rules;
    }

    int effectiveSteps(UUID player) {
        var current = rules;
        return current.individual() ? get(player) : current.universalSteps();
    }

    static float blocks(int steps) {
        return steps / 4.0f;
    }

    synchronized boolean savePersonal(UUID player, int steps) throws IOException {
        if (!rules.individual() || rules.adminsOnly()) {
            return false;
        }
        if (get(player) == steps) {
            return true;
        }
        var saved = properties(rules);
        setPersonalProperty(saved, player, steps);
        write(saved);
        updatePersonal(player, steps);
        return true;
    }

    synchronized boolean saveRules(Rules expected, Rules updated, UUID player, int personalSteps) throws IOException {
        if (rules != expected) {
            return false;
        }
        boolean personalChanged = updated.individual() && get(player) != personalSteps;
        if (rules.equals(updated) && !personalChanged) {
            return true;
        }
        var saved = properties(updated);
        if (personalChanged) {
            setPersonalProperty(saved, player, personalSteps);
        }
        write(saved);
        if (personalChanged) {
            updatePersonal(player, personalSteps);
        }
        rules = updated;
        return true;
    }

    private static void setPersonalProperty(Properties saved, UUID player, int steps) {
        if (steps == DEFAULT) {
            saved.remove(player.toString());
        } else {
            saved.setProperty(player.toString(), Integer.toString(steps));
        }
    }

    private void updatePersonal(UUID player, int steps) {
        if (steps == DEFAULT) {
            ranges.remove(player);
        } else {
            ranges.put(player, steps);
        }
    }

    private Properties properties(Rules rules) {
        var saved = new Properties();
        ranges.forEach((id, range) -> saved.setProperty(id.toString(), range.toString()));
        saved.setProperty("individual", Boolean.toString(rules.individual()));
        saved.setProperty("adminsOnly", Boolean.toString(rules.adminsOnly()));
        saved.setProperty("universalSteps", Integer.toString(rules.universalSteps()));
        return saved;
    }

    private void write(Properties saved) throws IOException {
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
    }
}
