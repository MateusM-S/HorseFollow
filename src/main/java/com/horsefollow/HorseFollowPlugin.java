package com.horsefollow;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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
    private final ItemConsume itemConsume;
    private Timer timer;
    
    // Instância estática para acesso do comando
    private static HorseFollowPlugin instance;

    public HorseFollowPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        Path dataDirectory = getDataDirectory();
        ensureDataPackManifest(dataDirectory);
        this.service = new FollowService(dataDirectory);
        this.itemConsume = new ItemConsume(service, this);
    }
    
    /**
     * Retorna a instância do plugin (para acesso do comando).
     */
    public static HorseFollowPlugin getInstance() {
        return instance;
    }
    
    /**
     * Retorna o ItemConsume (para acesso do comando).
     */
    public ItemConsume getItemConsume() {
        return itemConsume;
    }

    /**
     * Agenda a execução do "call" do Chifre após o tempo da carga (2s).
     * Usado pelo HornOnUseFilter quando o jogador usa o botão direito com o Horn.
     */
    public void scheduleHornCall(TimerTask task, long delayMs) {
        if (timer != null) {
            timer.schedule(task, delayMs);
        }
    }

    @Override
    public void setup() {
        CommandRegistry reg = getCommandRegistry();
        reg.registerCommand(new com.horsefollow.commands.HorseFollowCommand(service));

        // Feed pela tecla F: com Horse_Feed/Ram_Feed na mão, F = feed (bind); sem target não reduz o item.
        PacketAdapters.registerInbound(new FeedOnFKeyFilter(this));
        // Chifre (Horn): Use = chamar montaria vinculada + som do chifre.
        PacketAdapters.registerInbound(new HornOnUseFilter(this));

        // 10 ticks/s (100ms).
        timer = new Timer("HorseFollow-Tick", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                service.tick();
                // Verifica consumo de Horse_Feed em todos os jogadores online (não só vinculados)
                List<Ref<EntityStore>> onlineRefs = new ArrayList<>();
                try {
                    Universe.get().getPlayers().forEach(p -> {
                        Ref<EntityStore> ref = p.getReference();
                        if (ref != null && ref.isValid()) onlineRefs.add(ref);
                    });
                } catch (Throwable ignored) { }
                itemConsume.tick(onlineRefs);
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
        itemConsume.clearAll();
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
