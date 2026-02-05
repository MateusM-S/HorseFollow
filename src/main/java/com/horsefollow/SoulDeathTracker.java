package com.horsefollow;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda UUIDs de cavalos esqueleto (Horse_Skeleton, Horse_Skeleton_Armored) que acabaram de morrer.
 * Usado pelo Soul Amulet: só permite captura de alma quando o alvo está nessa lista (corpo recém-morto).
 */
public final class SoulDeathTracker {

    /** UUID da entidade -> timestamp (ms) em que morreu. Limpamos entradas mais antigas que RECENT_MS. */
    private static final Map<UUID, Long> recentlyDead = new ConcurrentHashMap<>();
    private static final long RECENT_MS = 60_000L; // 60 segundos para usar o amuleto no cadáver

    public static void onSoulNpcDeath(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) return;
        int roleIndex = npc.getRoleIndex();
        int horseSkeleton = NPCPlugin.get().getIndex("Horse_Skeleton");
        int horseSkeletonArmored = NPCPlugin.get().getIndex("Horse_Skeleton_Armored");
        if (roleIndex != horseSkeleton && roleIndex != horseSkeletonArmored) return;
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        recentlyDead.put(uuidComp.getUuid(), System.currentTimeMillis());
    }

    /** Retorna true se o UUID é de um cavalo esqueleto que morreu recentemente (ainda dentro da janela). */
    public static boolean isRecentlyDead(UUID entityUuid) {
        if (entityUuid == null) return false;
        Long at = recentlyDead.get(entityUuid);
        if (at == null) return false;
        if (System.currentTimeMillis() - at > RECENT_MS) {
            recentlyDead.remove(entityUuid);
            return false;
        }
        return true;
    }

    /** Remove o UUID da lista (ex.: após captura da alma). */
    public static void remove(UUID entityUuid) {
        if (entityUuid != null) recentlyDead.remove(entityUuid);
    }

    /** UUIDs que morreram recentemente (ainda dentro da janela). Usado pelo scan do amuleto (critério único). */
    public static java.util.Set<UUID> getRecentlyDeadUuids() {
        long now = System.currentTimeMillis();
        java.util.Set<UUID> out = new java.util.HashSet<>();
        recentlyDead.forEach((uuid, at) -> {
            if (now - at <= RECENT_MS) out.add(uuid);
        });
        return out;
    }

    /** Limpa entradas expiradas (pode ser chamado periodicamente). */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        recentlyDead.entrySet().removeIf(e -> now - e.getValue() > RECENT_MS);
    }
}
