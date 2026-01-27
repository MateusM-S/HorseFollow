package com.horsefollow;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HorseFollowPlugin extends JavaPlugin {

    private final FollowService service;
    private Timer timer;

    public HorseFollowPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        Path dataDirectory = getDataDirectory();
        ensureDataPackManifest(dataDirectory);
        this.service = new FollowService(dataDirectory);
    }

    @Override
    public void setup() {
        CommandRegistry reg = getCommandRegistry();
        reg.registerCommand(new com.horsefollow.commands.HorseFollowCommand(service));

        // 10 ticks/s (100ms).
        timer = new Timer("HorseFollow-Tick", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                service.tick();
            }
        }, 250L, 100L);
    }

    @Override
    public void shutdown() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        service.shutdown();
    }

    /**
     * Workaround:
     * o AssetModule pode varrer pastas de "dados" do mod como se fossem packs e exigir um manifest.json.
     * Criando um manifest mínimo aqui, o warning some nos próximos boots (o primeiro boot ainda pode avisar).
     */
    private static void ensureDataPackManifest(Path dataDirectory) {
        if (dataDirectory == null) return;
        try {
            Files.createDirectories(dataDirectory);
        } catch (Exception ignored) {
            return;
        }

        Path manifestPath = dataDirectory.resolve("manifest.json");
        if (Files.exists(manifestPath)) return;

        String version = tryReadModVersionFromClasspath();
        String json = "{\n"
                + "  \"Group\": \"HF\",\n"
                + "  \"Name\": \"HF_HorseFollow\",\n"
                + "  \"Version\": \"" + escapeJson(version) + "\",\n"
                + "  \"Description\": \"Pasta de dados do mod HorseFollow (binds/config).\",\n"
                + "  \"Authors\": [ { \"Name\": \"HorseFollow\" } ],\n"
                + "  \"DisabledByDefault\": true,\n"
                + "  \"IncludesAssetPack\": false\n"
                + "}\n";

        try {
            Files.writeString(
                    manifestPath,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
        } catch (Exception ignored) {
            // não pode quebrar o plugin por causa de I/O
        }
    }

    private static String tryReadModVersionFromClasspath() {
        // Lê /manifest.json do próprio jar e extrai o campo "Version".
        // Se falhar, retorna um valor neutro (a versão é só para o AssetModule aceitar).
        try (InputStream in = HorseFollowPlugin.class.getResourceAsStream("/manifest.json")) {
            if (in == null) return "0.0.0";
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"Version\"\\s*:\\s*\"([^\"]+)\"").matcher(raw);
            if (m.find()) {
                String v = m.group(1);
                return (v != null && !v.isBlank()) ? v.trim() : "0.0.0";
            }
            return "0.0.0";
        } catch (Exception ignored) {
            return "0.0.0";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
