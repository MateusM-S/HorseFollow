package com.horsefollow;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Sistema ECS que escuta quando um NPC recebe DeathComponent (morreu).
 * Se for Horse_Skeleton ou Horse_Skeleton_Armored, registra no SoulDeathTracker
 * para o Soul Amulet poder capturar só nesses cadáveres.
 */
public final class SoulDeathTrackerSystem extends DeathSystems.OnDeathSystem {

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(DeathComponent.getComponentType(), NPCEntity.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        SoulDeathTracker.onSoulNpcDeath(ref, store);
    }
}
