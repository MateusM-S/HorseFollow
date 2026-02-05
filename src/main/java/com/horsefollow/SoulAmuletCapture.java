package com.horsefollow;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captura de alma: critério único — DeathSystems registrou o UUID do cavalo que morreu;
 * jogador usa Primary com Soul_Amulet dentro de 8 blocos → troca item (amuleto com cavalo dentro) e remove cadáver.
 */
public final class SoulAmuletCapture {

    private static final String SOUL_AMULET_ITEM_ID = "Soul_Amulet";

    /** Fallback quando a metadata do item não persiste: última alma capturada por jogador (playerUuid -> data). */
    private static final Map<UUID, ItemConsume.SoulAmuletData> lastCapturedByPlayerUuid = new ConcurrentHashMap<>();
    private static final double CAPTURE_RANGE = 8.0;

    /**
     * Chamado na world thread. Critério: uso Primary + algum UUID em SoulDeathTracker tem entidade morta no alcance.
     * Troca o item na mão por amuleto "cheio" e remove a entidade.
     */
    public static void tryCaptureOnlyIfRecentlyDead(Store<EntityStore> store, Ref<EntityStore> playerRef, ItemConsume itemConsume) {
        if (store == null || playerRef == null || !playerRef.isValid()) return;
        java.util.Set<java.util.UUID> uuids = SoulDeathTracker.getRecentlyDeadUuids();
        if (uuids.isEmpty()) return;
        Ref<EntityStore> targetRef = FollowService.findDeadEntityWithUuidInRange(store, playerRef, CAPTURE_RANGE, uuids);
        if (targetRef == null || !targetRef.isValid()) return;
        UUIDComponent uuidComp = store.getComponent(targetRef, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
        int roleIndex = npc != null ? npc.getRoleIndex() : -1;

        if (invokeNativeCapture(store, playerRef, targetRef)) {
            SoulDeathTracker.remove(uuidComp.getUuid());
            return;
        }
        if (doManualCapture(store, playerRef, targetRef, uuidComp.getUuid(), roleIndex, itemConsume)) {
            SoulDeathTracker.remove(uuidComp.getUuid());
            UUID playerUuid = getPlayerUuid(store, playerRef);
            if (playerUuid != null) {
                lastCapturedByPlayerUuid.put(playerUuid, new ItemConsume.SoulAmuletData(uuidComp.getUuid(), roleIndex));
                SoulAmuletPersistence.save(playerUuid, uuidComp.getUuid(), roleIndex);
            }
        }
    }

    /**
     * Tenta chamar a lógica nativa de captura. Retorna true se conseguiu.
     */
    private static boolean invokeNativeCapture(Store<EntityStore> store, Ref<EntityStore> playerRef, Ref<EntityStore> targetRef) {
        Class<?> cls = null;
        try {
            cls = Class.forName("com.hypixel.hytale.builtin.adventure.farming.interactions.UseCaptureCrateInteraction");
            Object entityStore = store.getExternalData();
            // Tentativa: runCapture(EntityStore, Ref, Ref, String)
            try {
                Method run = cls.getMethod("runCapture", entityStore.getClass(), Ref.class, Ref.class, String.class);
                run.invoke(null, entityStore, playerRef, targetRef, SOUL_AMULET_ITEM_ID);
                return true;
            } catch (NoSuchMethodException ignored) {}
            Method run = cls.getMethod("runCapture", Store.class, Ref.class, Ref.class, String.class);
            run.invoke(null, store, playerRef, targetRef, SOUL_AMULET_ITEM_ID);
            return true;
        } catch (NoSuchMethodException e) {
            if (cls != null) {
                try {
                    Method run = cls.getMethod("capture", Store.class, Ref.class, Ref.class);
                    run.invoke(null, store, playerRef, targetRef);
                    return true;
                } catch (Exception ignored) {}
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Captura manual: coloca amuleto "cheio" na mão e tenta remover a entidade.
     * Se a troca do item funcionar, consideramos sucesso mesmo se destroy falhar (o cadáver some sozinho).
     */
    private static boolean doManualCapture(Store<EntityStore> store, Ref<EntityStore> playerRef, Ref<EntityStore> targetRef,
                                          java.util.UUID npcUuid, int roleIndex, ItemConsume itemConsume) {
        if (!itemConsume.replaceHeldWithFilledSoulAmulet(store, playerRef, npcUuid, roleIndex)) {
            return false;
        }
        destroyEntity(store, targetRef);
        sendSuccessMessage(store, playerRef);
        return true;
    }

    private static void sendSuccessMessage(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        try {
            com.hypixel.hytale.server.core.universe.PlayerRef player = store.getComponent(playerRef, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if (player != null) {
                String msg = Localization.get(store, playerRef, "horsefollow.soul_capture.ok");
                if (msg != null && !msg.isEmpty()) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw(msg));
                }
            }
        } catch (Throwable ignored) {}
    }

    /** Remove a entidade do mundo. Usa Store.removeEntity (API oficial). Público para SoulAmuletRelease.recollect. */
    public static boolean destroyEntity(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (store == null || targetRef == null || !targetRef.isValid()) return false;
        try {
            store.removeEntity(targetRef, RemoveReason.REMOVE);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static UUID getPlayerUuid(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        try {
            com.hypixel.hytale.server.core.universe.PlayerRef pr = store.getComponent(playerRef, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            return pr != null ? pr.getUuid() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Fallback quando getHeldSoulAmuletData retorna null (metadata do item não persistiu).
     * Só retorna dados se o jogador estiver com Soul_Amulet na mão. Remove do cache ao retornar.
     */
    public static ItemConsume.SoulAmuletData takeLastCapturedSoulIfHoldingAmulet(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null) return null;
        UUID playerUuid = getPlayerUuid(store, playerRef);
        if (playerUuid == null) return null;
        if (!ItemConsume.isHoldingSoulAmulet(store, playerRef)) return null;
        return lastCapturedByPlayerUuid.remove(playerUuid);
    }

    /** Limpa o cache de última captura após spawn bem-sucedido (para não reusar para outro amuleto). */
    public static void clearLastCapturedForPlayer(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        UUID playerUuid = getPlayerUuid(store, playerRef);
        if (playerUuid != null) lastCapturedByPlayerUuid.remove(playerUuid);
    }
}
