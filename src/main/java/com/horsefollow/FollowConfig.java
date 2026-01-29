package com.horsefollow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FollowConfig {

    private static final double DEFAULT_TELEPORT_DISTANCE = 100.0;
    private static final double DEFAULT_BEHIND_OFFSET = 3.0;
    private static final int DEFAULT_MAX_TPS_PER_APPLY = 2;
    private static final int DEFAULT_TP_COOLDOWN_TICKS = 200;
    private static final double DEFAULT_FEED_BIND_RANGE = 3.0;

    private final double teleportDistance;
    private final double behindOffset;
    private final int maxTeleportsPerTick;
    private final int tpCooldownTicks;
    private final double feedBindRange;

    private FollowConfig(
            double teleportDistance,
            double behindOffset,
            int maxTeleportsPerTick,
            int tpCooldownTicks,
            double feedBindRange
    ) {
        this.teleportDistance = teleportDistance;
        this.behindOffset = behindOffset;
        this.maxTeleportsPerTick = maxTeleportsPerTick;
        this.tpCooldownTicks = tpCooldownTicks;
        this.feedBindRange = feedBindRange;
    }

    public static FollowConfig defaults() {
        return new FollowConfig(
                DEFAULT_TELEPORT_DISTANCE,
                DEFAULT_BEHIND_OFFSET,
                DEFAULT_MAX_TPS_PER_APPLY,
                DEFAULT_TP_COOLDOWN_TICKS,
                DEFAULT_FEED_BIND_RANGE
        );
    }

    public static FollowConfig load(Path path) {
        FollowConfig defaults = defaults();
        if (path == null) {
            return defaults;
        }
        if (!Files.exists(path)) {
            defaults.save(path);
            return defaults;
        }
        double teleportDistance = defaults.teleportDistance;
        double behindOffset = defaults.behindOffset;
        int maxTeleportsPerTick = defaults.maxTeleportsPerTick;
        int tpCooldownTicks = defaults.tpCooldownTicks;
        double feedBindRange = defaults.feedBindRange;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] parts = trimmed.split("=", 2);
                if (parts.length != 2) continue;
                String key = parts[0].trim().toLowerCase();
                String value = parts[1].trim();
                if (key.equals("teleportdistance")) {
                    teleportDistance = parseDouble(value, teleportDistance);
                } else if (key.equals("behindoffset")) {
                    behindOffset = parseDouble(value, behindOffset);
                } else if (key.equals("maxteleportspertick")) {
                    maxTeleportsPerTick = parseInt(value, maxTeleportsPerTick);
                } else if (key.equals("tpcooldownticks")) {
                    tpCooldownTicks = parseInt(value, tpCooldownTicks);
                } else if (key.equals("feedbindrange")) {
                    feedBindRange = parseDouble(value, feedBindRange);
                }
            }
        } catch (IOException ignored) {
            return defaults;
        }
        return new FollowConfig(teleportDistance, behindOffset, maxTeleportsPerTick, tpCooldownTicks, feedBindRange);
    }

    public void save(Path path) {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("# HorseFollow config");
            lines.add("teleportDistance=" + teleportDistance);
            lines.add("behindOffset=" + behindOffset);
            lines.add("maxTeleportsPerTick=" + maxTeleportsPerTick);
            lines.add("tpCooldownTicks=" + tpCooldownTicks);
            lines.add("feedBindRange=" + feedBindRange);
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // best effort
        }
    }

    public FollowConfig withTeleportDistance(double value) {
        return new FollowConfig(value, behindOffset, maxTeleportsPerTick, tpCooldownTicks, feedBindRange);
    }

    public double getTeleportDistance() {
        return teleportDistance;
    }

    public double getBehindOffset() {
        return behindOffset;
    }

    public int getMaxTeleportsPerTick() {
        return maxTeleportsPerTick;
    }

    public int getTpCooldownTicks() {
        return tpCooldownTicks;
    }

    /** Alcance (blocos) para vincular montaria ao usar Horse_Feed/Ram_Feed. */
    public double getFeedBindRange() {
        return feedBindRange;
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) return fallback;
        try {
            String normalized = value.trim().replace(',', '.');
            double parsed = Double.parseDouble(normalized);
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
