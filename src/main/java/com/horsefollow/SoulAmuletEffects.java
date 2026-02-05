package com.horsefollow;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collections;

/**
 * Efeitos visuais e sonoros do Amuleto de Alma (recolher/liberar).
 */
public final class SoulAmuletEffects {

    /** Cor teal/cyan para partículas do amuleto. */
    private static final Color PARTICLE_COLOR = new Color((byte) 29, (byte) 128, (byte) 154);

    /** IDs do som ao recolher a alma (vários formatos do Hytale). */
    private static final String[] SOUND_RECOLLECT_IDS = {
        "SFX/Deployable/Totem_Heal/Deployable_Totem_Heal_Despawn_01",
        "Deployable_Totem_Heal_Despawn_01",
        "SFX_Deployable_Totem_Heal_Despawn_01",
        "SFX/Items/Consume_Bread_Stereo_01",
        "SFX_Items_Consume_Bread_Stereo_01"
    };

    /** IDs do som ao liberar a alma. */
    private static final String[] SOUND_RELEASE_IDS = {
        "SFX/Deployable/Totem_Heal/Deployable_Totem_Heal_Despawn_03",
        "Deployable_Totem_Heal_Despawn_03",
        "SFX_Deployable_Totem_Heal_Despawn_03",
        "SFX/Items/Consume_Bread_Stereo_01",
        "SFX_Items_Consume_Bread_Stereo_01"
    };

    /** Sistema de partículas para efeito de alma (fallbacks). */
    private static final String[] PARTICLE_SYSTEM_IDS = {
        "Potion_Health_Implosion",
        "Hearts"
    };

    private static final float SOUND_VOLUME = 1.2f;

    /** Toca o som de recolhimento na posição. */
    public static void playRecollectEffects(Store<EntityStore> store, Ref<EntityStore> playerRef, Vector3d position) {
        playSoundAt(store, playerRef, position, SOUND_RECOLLECT_IDS);
        spawnParticleAt(store, playerRef, position);
    }

    /** Toca o som e partícula de liberação na posição. Coluna de partículas estilo "sai do chão". */
    public static void playReleaseEffects(Store<EntityStore> store, Ref<EntityStore> playerRef, Vector3d position) {
        playSoundAt(store, playerRef, position, SOUND_RELEASE_IDS);
        spawnRiseParticleColumn(store, playerRef, position);
    }

    private static void playSoundAt(Store<EntityStore> store, Ref<EntityStore> playerRef, Vector3d pos, String[] soundIds) {
        if (store == null || playerRef == null || !playerRef.isValid()) return;
        Integer index = getSoundEventIndex(soundIds);
        if (index == null || index < 0) return;
        PlayerRef pr = getPlayerRef(store, playerRef);
        if (pr == null) return;
        try {
            SoundUtil.playSoundEvent2dToPlayer(pr, index.intValue(), SoundCategory.SFX, SOUND_VOLUME, 1.0f);
        } catch (Throwable ignored) {
            if (pos != null) {
                try {
                    SoundUtil.playSoundEvent3dToPlayer(playerRef, index.intValue(), SoundCategory.SFX,
                        pos.getX(), pos.getY(), pos.getZ(), SOUND_VOLUME, 1.0f, store);
                } catch (Throwable t2) {}
            }
        }
    }

    private static PlayerRef getPlayerRef(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !entityRef.isValid()) return null;
        return store.getComponent(entityRef, PlayerRef.getComponentType());
    }

    private static Integer getSoundEventIndex(String[] ids) {
        for (String id : ids) {
            Integer idx = ItemConsume.tryGetSoundEventIndex(id);
            if (idx != null && idx >= 0) return idx;
        }
        return null;
    }

    private static void spawnParticleAt(Store<EntityStore> store, Ref<EntityStore> playerRef, Vector3d position) {
        if (store == null || playerRef == null || !playerRef.isValid() || position == null) return;
        String systemId = resolveParticleSystemId();
        if (systemId == null) return;
        try {
            ParticleUtil.spawnParticleEffect(systemId, position, 1f, 1f, 1f, 1f, PARTICLE_COLOR,
                Collections.singletonList(playerRef), store);
        } catch (Throwable ignored) {}
    }

    /** Coluna de partículas de baixo para cima (estilo Rekindle_Embers: cavalo "sai do chão"). */
    private static void spawnRiseParticleColumn(Store<EntityStore> store, Ref<EntityStore> playerRef, Vector3d basePos) {
        if (store == null || playerRef == null || !playerRef.isValid() || basePos == null) return;
        String systemId = resolveParticleSystemId();
        if (systemId == null) return;
        try {
            double x = basePos.getX();
            double z = basePos.getZ();
            for (int i = 0; i <= 3; i++) {
                double y = basePos.getY() - 0.5 + (i * 0.4);
                Vector3d p = new Vector3d(x, y, z);
                ParticleUtil.spawnParticleEffect(systemId, p, 1f, 1f, 1f, 1f, PARTICLE_COLOR,
                    Collections.singletonList(playerRef), store);
            }
        } catch (Throwable ignored) {}
    }

    private static String resolveParticleSystemId() {
        return PARTICLE_SYSTEM_IDS.length > 0 ? PARTICLE_SYSTEM_IDS[0] : null;
    }

    /** Retorna a posição do jogador ou de uma entidade. */
    public static Vector3d getPosition(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) return null;
        TransformComponent tf = store.getComponent(ref, TransformComponent.getComponentType());
        return tf != null ? tf.getPosition() : null;
    }
}
