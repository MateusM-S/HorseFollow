package com.horsefollow;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Filtro que intercepta Secondary (botão direito ou uso da barra de utilitários).
 * Na world thread verifica se o item usado é o Horn (na mão ou na utility bar);
 * se for, toca o som e agenda o "call" em 4s.
 */
public final class HornOnUseFilter implements PlayerPacketFilter {

    private static final int PACKET_ID_SYNC_INTERACTION_CHAINS = 290;

    private final HorseFollowPlugin plugin;

    public HornOnUseFilter(@Nonnull HorseFollowPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean test(PlayerRef playerRef, Packet packet) {
        if (packet == null || packet.getId() != PACKET_ID_SYNC_INTERACTION_CHAINS) {
            return false;
        }
        if (!(packet instanceof SyncInteractionChains)) {
            return false;
        }
        SyncInteractionChains chains = (SyncInteractionChains) packet;
        if (chains.updates == null) {
            return false;
        }
        for (SyncInteractionChain u : chains.updates) {
            if (u == null || u.interactionType != InteractionType.Secondary) {
                continue;
            }
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return false;
            }
            Store<EntityStore> store = entityRef.getStore();
            if (store == null) {
                return false;
            }
            EntityStore entityStore = store.getExternalData();
            if (entityStore == null) {
                return false;
            }
            String itemInHandId = u.itemInHandId;
            Ref<EntityStore> ref = entityRef;
            ItemConsume itemConsume = plugin.getItemConsume();
            ItemConsume.runOnWorldThread(entityStore, () ->
                    itemConsume.onSecondaryInteraction(store, ref, itemInHandId));
            return false;
        }
        return false;
    }
}
