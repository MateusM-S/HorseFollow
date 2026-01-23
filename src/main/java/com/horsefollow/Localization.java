package com.horsefollow;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Localization {

    private static final String DEFAULT_LOCALE = "en-US";
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private Localization() {}

    public static String get(Store<EntityStore> store, Ref<EntityStore> playerRef, String key) {
        if (key == null || key.isBlank()) return "";
        String locale = resolveLocale(store, playerRef);
        String value = lookup(locale, key);
        if (value != null) return value;
        if (!DEFAULT_LOCALE.equals(locale)) {
            value = lookup(DEFAULT_LOCALE, key);
            if (value != null) return value;
        }
        return key;
    }

    private static String resolveLocale(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store != null && playerRef != null) {
            PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (player != null && player.getLanguage() != null && !player.getLanguage().isBlank()) {
                return player.getLanguage();
            }
        }
        return DEFAULT_LOCALE;
    }

    private static String lookup(String locale, String key) {
        Map<String, String> map = CACHE.computeIfAbsent(normalizeLocale(locale), Localization::loadLocale);
        return map.get(key);
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) return DEFAULT_LOCALE;
        return locale.replace('_', '-');
    }

    private static Map<String, String> loadLocale(String locale) {
        String path = "Server/Languages/" + locale + "/server.lang";
        InputStream in = Localization.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int idx = trimmed.indexOf('=');
                if (idx <= 0) continue;
                String k = trimmed.substring(0, idx).trim();
                String v = trimmed.substring(idx + 1).trim();
                if (!k.isEmpty()) {
                    out.put(k, v);
                }
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return out;
    }
}
