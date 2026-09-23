package com.wilder0p.firstjoinreward;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Shared files so Paper, Fabric, and home Velocity agree on who was just
 * on survival-side (paper survival or fabric).
 */
final class EmptyRewardNet {
    static final Path ROOT = Path.of("/mnt/pool/skygate/empty-reward");
    static final Path LAST_SEEN = ROOT.resolve("last-seen");

    private EmptyRewardNet() {}

    static void touchLastSeen(UUID uuid) {
        if (uuid == null) {
            return;
        }
        try {
            Files.createDirectories(LAST_SEEN);
            Files.writeString(
                    LAST_SEEN.resolve(uuid.toString()),
                    Long.toString(System.currentTimeMillis()),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (Exception ignored) {
        }
    }

    static boolean seenRecently(UUID uuid, long graceMs) {
        if (uuid == null || graceMs <= 0L) {
            return false;
        }
        try {
            Path file = LAST_SEEN.resolve(uuid.toString());
            if (!Files.isRegularFile(file)) {
                return false;
            }
            String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            long then = Long.parseLong(raw);
            return System.currentTimeMillis() - then < graceMs;
        } catch (Exception ignored) {
            return false;
        }
    }

    static void writeOnline(String side, int count) {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(
                    ROOT.resolve(side + ".online"),
                    count + "\n" + System.currentTimeMillis(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (Exception ignored) {
        }
    }

    static int freshOnline(String side, long maxAgeMs) {
        try {
            Path file = ROOT.resolve(side + ".online");
            if (!Files.isRegularFile(file)) {
                return 0;
            }
            String[] lines = Files.readString(file, StandardCharsets.UTF_8).trim().split("\\R");
            if (lines.length < 2) {
                return 0;
            }
            long then = Long.parseLong(lines[1].trim());
            if (System.currentTimeMillis() - then > maxAgeMs) {
                return 0;
            }
            return Math.max(0, Integer.parseInt(lines[0].trim()));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
