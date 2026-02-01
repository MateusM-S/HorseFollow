package com.horsefollow.ui;

import com.horsefollow.FollowService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import au.ellie.hyui.builders.PageBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Abre a página de configuração do HorseFollow via HyUI (3 abas: Menu, Configuração, Sobre).
 * HTML em Common/UI/Custom/Pages/ConfigPage_HyUI.html.
 * Dependência: libs/HyUI-0.5.10-all.jar (au.ellie.hyui).
 *
 * Importante:
 * - Checkboxes: o estado visual só é garantido na abertura da página (HTML inicial).
 * - Para evitar travar botões/bindings, ao SALVAR: salva e FECHA a UI.
 * - O jogador reabre manualmente (comando/ação) e a UI vem com valores persistidos.
 */
public final class ConfigPageHyUI {

    private static final String HTML_RESOURCE = "/Common/UI/Custom/Pages/ConfigPage_HyUI.html";

    private ConfigPageHyUI() {}

    /** Indica se o valor do checkbox está marcado (HyUI pode enviar "true", "on", true, "1", etc.). */
    private static boolean isCheckboxChecked(java.util.Optional<?> valueOpt) {
        if (valueOpt == null || valueOpt.isEmpty()) return false;
        Object v = valueOpt.get();
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "on".equals(s) || "1".equals(s) || "yes".equals(s) || "checked".equals(s);
    }

    /** Interpreta o valor do input de distância; retorna 100.0 se vazio ou inválido. */
    private static double parseDistanceOr100(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return 100.0;
        try {
            double v = Double.parseDouble(rawValue.trim().replace(',', '.'));
            return v >= 0 ? v : 100.0;
        } catch (NumberFormatException e) {
            return 100.0;
        }
    }

    /** Escapa valor para uso em atributo HTML value="...". */
    private static String escapeHtmlAttr(@Nonnull String s) {
        if (s.isEmpty()) return s;
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Carrega o HTML do recurso e injeta placeholder do nome, imagem, vida/estamina/velocidade e config.
     *
     * ATENÇÃO: aqui é onde “persiste” visualmente:
     * - checkbox só vem marcado se o HTML inicial tiver checked="checked"
     */
    private static String loadHtml(double currentDistance,
                                   boolean disableTeleport,
                                   boolean disableFollow,
                                   @Nonnull String mountImgSrc,
                                   @Nonnull String mountName,
                                   int lifeCurrent, int lifeMax,
                                   int staminaCurrent, int staminaMax,
                                   int maxSpeed) throws IOException {

        try (InputStream in = ConfigPageHyUI.class.getResourceAsStream(HTML_RESOURCE)) {
            if (in == null) throw new IOException("Resource not found: " + HTML_RESOURCE);

            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            html = html.replace("MOUNT_PLACEHOLDER", "Nomeie sua montaria");
            html = html.replace("MOUNT_NAME_VALUE", escapeHtmlAttr(mountName));
            html = html.replace("MOUNT_IMG_SRC", mountImgSrc);

            html = html.replace("value=\"LIFE_PLACEHOLDER\"", "value=\"" + lifeCurrent + "\"");
            html = html.replace("max=\"LIFE_MAX_PLACEHOLDER\"", "max=\"" + lifeMax + "\"");
            html = html.replace("value=\"STAMINA_PLACEHOLDER\"", "value=\"" + staminaCurrent + "\"");
            html = html.replace("max=\"STAMINA_MAX_PLACEHOLDER\"", "max=\"" + staminaMax + "\"");

            html = html.replace("VELOCITY_TEXT_PLACEHOLDER", "Velocidade: " + maxSpeed + " blocos/s");

            String distanceStr = String.format(Locale.ROOT, "%.1f", currentDistance);
            html = html.replace("CONFIG_DISTANCE_VALUE", distanceStr);

            // CHECKBOX: usar atributo completo (mais estável no HYUI)
            html = html.replace("CONFIG_DISABLE_TP_CHECKED", disableTeleport ? "checked=\"checked\"" : "");
            html = html.replace("CONFIG_DISABLE_FOLLOW_CHECKED", disableFollow ? "checked=\"checked\"" : "");

            // Se ainda existir placeholder antigo de value no seu HTML, neutraliza
            html = html.replace("CONFIG_DISABLE_TP_VALUE", "");
            html = html.replace("CONFIG_DISABLE_FOLLOW_VALUE", "");

            html = html.replace("CONFIG_INPUT_DISABLED", "");

            return html;
        }
    }

    /**
     * Aplica o nome digitado ao nameplate da montaria.
     */
    private static void applyMountName(@Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> playerRef,
                                       Ref<EntityStore> horseRef,
                                       @Nonnull String nameInput,
                                       @Nonnull PlayerRef playerRefComponent) {
        if (horseRef == null || !horseRef.isValid()) {
            playerRefComponent.sendMessage(Message.raw("[HorseFollow] Nenhuma montaria vinculada."));
            return;
        }
        if (nameInput.isBlank()) {
            playerRefComponent.sendMessage(Message.raw("[HorseFollow] Digite um nome no campo e clique no botão para nomear a montaria."));
            return;
        }
        boolean ok = MountNameplateHelper.setNameplate(store, horseRef, nameInput, playerRef, (text, entityUuid) -> {});
        playerRefComponent.sendMessage(Message.raw(ok
                ? "[HorseFollow] Nome da montaria alterado para: " + nameInput
                : "[HorseFollow] Não foi possível alterar o nome da montaria."));
    }

    /**
     * Fecha a página do jogador (deve ser chamado na world thread).
     */
    private static void closePage(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            var pm = player.getPageManager();
            if (pm != null) {
                pm.setPage(playerRef, store, Page.None);
            }
        }
    }

    /**
     * Abre a página HyUI (Menu / Configuração / Sobre) para o jogador.
     * Ao SALVAR: salva e FECHA a UI (sem recarregar), evitando travar botões.
     */
    public static void open(@Nonnull Ref<EntityStore> playerRef,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull PlayerRef playerRefComponent,
                            @Nonnull FollowService service,
                            @Nonnull Consumer<Runnable> runOnWorldThread) {

        runOnWorldThread.accept(() -> {
            try {
                UUID playerUuid = playerRefComponent.getUuid();

                double currentDistance = service.getConfig().getTeleportDistance();
                boolean disableTeleport = service.isTeleportDisabled(playerUuid);
                boolean disableFollow = service.isFollowDisabled(playerUuid);

                String mountType = service.getMountTypeName(store, playerRef);
                String mountImgSrc = "Ram".equals(mountType) ? "Ram@2x.png" : "Horse@2x.png";

                Ref<EntityStore> horseRef = service.getBoundHorse(playerRef);
                String mountName = (horseRef != null && horseRef.isValid())
                        ? MountNameplateHelper.getNameplateText(store, horseRef)
                        : "";
                String safeMountName = Objects.requireNonNullElse(mountName, "");

                FollowService.MountDisplayStats stats = service.getMountDisplayStats(store, playerRef);

                String html = loadHtml(
                        currentDistance, disableTeleport, disableFollow,
                        mountImgSrc, Objects.requireNonNull(safeMountName, "mountName"),
                        stats.currentHealth, stats.maxHealth,
                        stats.currentStamina, stats.maxStamina,
                        stats.maxSpeed
                );

                Runnable closeAction = () -> closePage(playerRef, store);

                PageBuilder.pageForPlayer(playerRefComponent)
                        .fromHtml(html)

                        // Checkbox "Desativar teleporte": ao clicar, persiste (ctx pode não enviar valor; fallback = toggle)
                        .addEventListener("DisableTeleport", CustomUIEventBindingType.Activating, (data, ctx) -> {
                            java.util.Optional<?> v = ctx.getValue("DisableTeleport");
                            final boolean newVal;
                            if (v != null && v.isPresent() && v.get() != null) {
                                newVal = isCheckboxChecked(v);
                            } else {
                                // Cliente não envia valor no evento do checkbox: alternar estado atual no servidor
                                newVal = !service.isTeleportDisabled(playerUuid);
                            }
                            final boolean toApply = newVal;
                            runOnWorldThread.accept(() -> {
                                service.setTeleportDisabled(playerRefComponent.getUuid(), toApply);
                                playerRefComponent.sendMessage(Message.raw(toApply
                                        ? "[HorseFollow] Teleporte automático desativado."
                                        : "[HorseFollow] Teleporte automático ativado."));
                            });
                        })

                        // Checkbox "Desativar acompanhamento": ao clicar, persiste (fallback = toggle)
                        .addEventListener("DisableFollow", CustomUIEventBindingType.Activating, (data, ctx) -> {
                            java.util.Optional<?> v = ctx.getValue("DisableFollow");
                            final boolean newVal;
                            if (v != null && v.isPresent() && v.get() != null) {
                                newVal = isCheckboxChecked(v);
                            } else {
                                newVal = !service.isFollowDisabled(playerUuid);
                            }
                            final boolean toApply = newVal;
                            runOnWorldThread.accept(() -> {
                                service.setFollowDisabled(playerRefComponent.getUuid(), toApply);
                                playerRefComponent.sendMessage(Message.raw(toApply
                                        ? "[HorseFollow] Acompanhamento da montaria desativado."
                                        : "[HorseFollow] Acompanhamento da montaria ativado."));
                            });
                        })

                        // SALVAR: distância + fecha a UI (checkboxes já persistem ao clicar)
                        .addEventListener("SaveButton", CustomUIEventBindingType.Activating, (data, ctx) -> {
                            String rawValue = ctx.getValue("DistanceInput")
                                    .map(v -> String.valueOf(v).trim().replace(',', '.'))
                                    .orElse("");
                            double newDistance = parseDistanceOr100(rawValue);

                            runOnWorldThread.accept(() -> {
                                service.updateTeleportDistance(newDistance);
                                playerRefComponent.sendMessage(Message.raw("[HorseFollow] Configurações salvas."));
                                closePage(playerRef, store);
                            });
                        })

                        // Nomear montaria
                        .addEventListener("ResetName", CustomUIEventBindingType.Activating, (data, ctx) -> {
                            String nameInput = ctx.getValue("MountName").map(v -> String.valueOf(v).trim()).orElse("");
                            runOnWorldThread.accept(() ->
                                    applyMountName(store, playerRef, horseRef, Objects.requireNonNull(nameInput, "nameInput"), playerRefComponent)
                            );
                        })

                        // Sair
                        .addEventListener("SairConfig", CustomUIEventBindingType.Activating, (data, ctx) -> runOnWorldThread.accept(closeAction))
                        .addEventListener("SairMenu", CustomUIEventBindingType.Activating, (data, ctx) -> runOnWorldThread.accept(closeAction))
                        .addEventListener("SairAbout", CustomUIEventBindingType.Activating, (data, ctx) -> runOnWorldThread.accept(closeAction))

                        .open(store);

            } catch (IOException e) {
                playerRefComponent.sendMessage(Message.raw("[HorseFollow] Erro ao carregar a UI: " + e.getMessage()));
            } catch (Throwable t) {
                String msg = t.getClass().getSimpleName() + ": " + (t.getMessage() != null ? t.getMessage() : t.toString());
                playerRefComponent.sendMessage(Message.raw("[HorseFollow] Erro HyUI: " + msg));
                try { t.printStackTrace(); } catch (Throwable ignored) {}
            }
        });
    }
}
