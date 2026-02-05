package com.horsefollow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persiste a alma capturada por jogador em souls.txt (playerUuid=npcUuid,roleIndex).
 * Permite usar npcNameKey como chave de tradução para exibição ("Alma capturada") em vez de "soul:uuid".
 */
public final class SoulAmuletPersistence {

    private static Path soulsFile;
    private static final Map<UUID, ItemConsume.SoulAmuletData> cache = new ConcurrentHashMap<>();
    private static final Object ioLock = new Object();

    public static void init(Path dataDirectory) {
        soulsFile = dataDirectory != null ? dataDirectory.resolve("souls.txt") : null;
        load();
    }

    public static void save(UUID playerUuid, UUID npcUuid, int roleIndex) {
        if (playerUuid == null || npcUuid == null) return;
        cache.put(playerUuid, new ItemConsume.SoulAmuletData(npcUuid, roleIndex));
        saveToFile();
    }

    public static ItemConsume.SoulAmuletData load(UUID playerUuid) {
        if (playerUuid == null) return null;
        return cache.get(playerUuid);
    }

    private static void load() {
        if (soulsFile == null || !Files.exists(soulsFile)) return;
        synchronized (ioLock) {
            try {
                for (String line : Files.readAllLines(soulsFile, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    int eq = trimmed.indexOf('=');
                    if (eq <= 0) continue;
                    String playerStr = trimmed.substring(0, eq).trim();
                    String valueStr = trimmed.substring(eq + 1).trim();
                    int comma = valueStr.indexOf(',');
                    if (comma <= 0) continue;
                    String npcStr = valueStr.substring(0, comma).trim();
                    String roleStr = valueStr.substring(comma + 1).trim();
                    try {
                        UUID playerUuid = UUID.fromString(playerStr);
                        UUID npcUuid = UUID.fromString(npcStr);
                        int roleIndex = Integer.parseInt(roleStr);
                        cache.put(playerUuid, new ItemConsume.SoulAmuletData(npcUuid, roleIndex));
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (IOException ignored) {}
        }
    }

    private static void saveToFile() {
        if (soulsFile == null) return;
        synchronized (ioLock) {
            try {
                Files.createDirectories(soulsFile.getParent());
                StringBuilder sb = new StringBuilder();
                sb.append("# Alma capturada por jogador (playerUuid=npcUuid,roleIndex)\n");
                for (Map.Entry<UUID, ItemConsume.SoulAmuletData> e : cache.entrySet()) {
                    sb.append(e.getKey()).append('=')
                            .append(e.getValue().soulUuid).append(',')
                            .append(e.getValue().roleIndex).append('\n');
                }
                Files.writeString(soulsFile, sb.toString(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }
    }
}
