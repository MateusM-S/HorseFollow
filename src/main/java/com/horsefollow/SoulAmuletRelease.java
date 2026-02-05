package com.horsefollow;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import java.util.UUID;

/**
 * Solta o cavalo esqueleto do amuleto cheio (Secondary): spawn com mesmo UUID, role _Friendly.
 * Só spawna se não existir entidade com esse UUID no mundo.
 */
public final class SoulAmuletRelease {

    private static final double SPAWN_OFFSET = 2.0;
    /** Distância jogador–cavalo para recolher (5 blocos). */
    private static final double RECOLLECT_RANGE = 5.0;
    /** Margem em blocos: target (onde mira) deve cair dentro da “caixa” do cavalo (centro ± margem). */
    private static final double RECOLLECT_TARGET_MARGIN_XZ = 2.5;
    private static final double RECOLLECT_TARGET_MARGIN_Y_UP = 2.5;
    private static final double RECOLLECT_TARGET_MARGIN_Y_DOWN = 0.8;

    /**
     * Chamado na world thread. Se o item na mão for amuleto cheio (ou dados em cache):
     * - Se o cavalo está vivo, vinculado ao jogador e no alcance: recolhe (despawn, unbind), amuleto permanece cheio.
     * - Se o cavalo não está no mundo: spawna com mesmo UUID, role _Friendly e faz bind.
     * @param dataOverride quando a metadata do item não persiste, o filtro pode passar a última alma capturada (cache).
     * @param targetSpawnPos posição do target (onde o jogador mira) para spawn; null = ao lado do jogador.
     */
    public static void tryRelease(Store<EntityStore> store, Ref<EntityStore> playerRef, ItemConsume.SoulAmuletData dataOverride,
                                  FollowService followService, Vector3d targetSpawnPos) {
        if (store == null || playerRef == null || !playerRef.isValid()) return;
        ItemConsume.SoulAmuletData data = dataOverride != null ? dataOverride : ItemConsume.getHeldSoulAmuletData(store, playerRef);
        if (data == null) return;
        UUID soulUuid = data.soulUuid;
        if (soulUuid == null) return;

        Ref<EntityStore> horseRef = FollowService.findLivingEntityByUuid(store, soulUuid);
        if (horseRef != null && horseRef.isValid()) {
            // Cavalo vivo: pode recolher se vinculado, perto (5 blocos) e mirando no cavalo (target na caixa).
            if (followService != null && soulUuid.equals(followService.getBoundHorseUuid(playerRef))) {
                if (isInRange(store, playerRef, horseRef, RECOLLECT_RANGE)
                        && isTargetOnHorse(store, horseRef, targetSpawnPos)) {
                    Vector3d horsePos = SoulAmuletEffects.getPosition(store, horseRef);
                    followService.unbindForRecollect(playerRef);
                    if (horsePos != null) SoulAmuletEffects.playRecollectEffects(store, playerRef, horsePos);
                    if (SoulAmuletCapture.destroyEntity(store, horseRef)) {
                        sendMessage(store, playerRef, "horsefollow.soul_recollect.ok");
                    }
                    return;
                }
            }
            return;
        }
        Vector3d spawnPos = resolveSpawnPosition(store, playerRef, targetSpawnPos);
        if (spawnPos == null) return;
        Vector3f rotation = new Vector3f(0, 0, 0);
        // Spawn com role BASE (Horse_Skeleton ou Horse_Skeleton_Armored); o bind aplica o _Friendly
        // para que RoleChangeSystem aplique corretamente MaxHealth, MaxSpeed, DropList etc.
        String roleName = resolveSpawnRoleName(data.roleIndex);
        if (roleName == null) return;
        Object pair = spawnNpc(store, roleName, spawnPos, rotation);
        if (pair == null) return;
        Ref<EntityStore> npcRef = getPairFirst(pair);
        if (npcRef != null && npcRef.isValid()) {
            setEntityUuid(store, npcRef, soulUuid);
            SoulAmuletEffects.playReleaseEffects(store, playerRef, spawnPos);
            if (followService != null) {
                followService.bind(playerRef, npcRef);
            }
            sendMessage(store, playerRef, "horsefollow.soul_release.ok");
        }
    }

    /** Resolve posição de spawn: target (onde o jogador mira) ou fallback ao lado do jogador. */
    private static Vector3d resolveSpawnPosition(Store<EntityStore> store, Ref<EntityStore> playerRef, Vector3d targetPos) {
        if (targetPos != null) {
            double y = Math.floor(targetPos.getY()) + 0.1;
            return new Vector3d(targetPos.getX(), y, targetPos.getZ());
        }
        TransformComponent tf = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (tf == null) return null;
        Vector3d pos = tf.getPosition();
        if (pos == null) return null;
        double spawnY = Math.floor(pos.getY()) + 0.1;
        return new Vector3d(pos.getX() + SPAWN_OFFSET, spawnY, pos.getZ());
    }

    /** Retorna o role BASE para spawn (Horse_Skeleton ou Horse_Skeleton_Armored). O bind aplica _Friendly depois. */
    private static String resolveSpawnRoleName(int roleIndex) {
        int idxSkeleton = NPCPlugin.get().getIndex("Horse_Skeleton");
        int idxArmored = NPCPlugin.get().getIndex("Horse_Skeleton_Armored");
        if (roleIndex == idxSkeleton) return "Horse_Skeleton";
        if (roleIndex == idxArmored) return "Horse_Skeleton_Armored";
        if (NPCPlugin.get().getIndex("Horse_Skeleton") >= 0) return "Horse_Skeleton";
        return "Horse_Skeleton_Armored";
    }

    private static Object spawnNpc(Store<EntityStore> store, String roleName, Vector3d position, Vector3f rotation) {
        try {
            Object npcPlugin = NPCPlugin.get();
            java.lang.reflect.Method spawn = npcPlugin.getClass().getMethod("spawnNPC",
                    Store.class, String.class, String.class, Vector3d.class, Vector3f.class);
            return spawn.invoke(npcPlugin, store, roleName, null, position, rotation);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Ref<EntityStore> getPairFirst(Object pair) {
        if (pair == null) return null;
        try {
            java.lang.reflect.Method first = pair.getClass().getMethod("first");
            Object ref = first.invoke(pair);
            return ref instanceof Ref ? (Ref<EntityStore>) ref : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setEntityUuid(Store<EntityStore> store, Ref<EntityStore> entityRef, UUID uuid) {
        try {
            store.replaceComponent(entityRef, UUIDComponent.getComponentType(), new UUIDComponent(uuid));
        } catch (Throwable t) { }
    }

    /** Target (onde o jogador mira) deve cair na “caixa” do cavalo (corpo inteiro), não só nos pés. */
    private static boolean isTargetOnHorse(Store<EntityStore> store, Ref<EntityStore> horseRef, Vector3d targetPos) {
        if (targetPos == null) return false;
        Vector3d hp = SoulAmuletEffects.getPosition(store, horseRef);
        if (hp == null) return false;
        double dx = Math.abs(targetPos.getX() - hp.getX());
        double dz = Math.abs(targetPos.getZ() - hp.getZ());
        if (dx > RECOLLECT_TARGET_MARGIN_XZ || dz > RECOLLECT_TARGET_MARGIN_XZ) return false;
        double dy = targetPos.getY() - hp.getY();
        return dy >= -RECOLLECT_TARGET_MARGIN_Y_DOWN && dy <= RECOLLECT_TARGET_MARGIN_Y_UP;
    }

    private static boolean isInRange(Store<EntityStore> store, Ref<EntityStore> playerRef, Ref<EntityStore> horseRef, double range) {
        TransformComponent ptf = store.getComponent(playerRef, TransformComponent.getComponentType());
        TransformComponent htf = store.getComponent(horseRef, TransformComponent.getComponentType());
        if (ptf == null || htf == null) return false;
        Vector3d pp = ptf.getPosition();
        Vector3d hp = htf.getPosition();
        if (pp == null || hp == null) return false;
        double dx = pp.getX() - hp.getX();
        double dy = pp.getY() - hp.getY();
        double dz = pp.getZ() - hp.getZ();
        return (dx * dx + dy * dy + dz * dz) <= (range * range);
    }

    private static void sendMessage(Store<EntityStore> store, Ref<EntityStore> playerRef, String localeKey) {
        try {
            PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (player != null) {
                String msg = Localization.get(store, playerRef, localeKey);
                if (msg != null && !msg.isEmpty()) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw(msg));
                }
            }
        } catch (Throwable ignored) {}
    }
}
