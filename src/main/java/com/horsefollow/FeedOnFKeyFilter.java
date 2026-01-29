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
 * Filtro de packet que intercepta a tecla F (SyncInteractionChains, Use) quando o jogador
 * está com Horse_Feed ou Ram_Feed na mão. Cancela o Mount e executa feed (bind) na world thread:
 * só reduz 1 do item quando houver target no alcance; sem target não altera o inventário.
 */
public final class FeedOnFKeyFilter implements PlayerPacketFilter {

    private static final int PACKET_ID_SYNC_INTERACTION_CHAINS = 290;

    private final HorseFollowPlugin plugin;

    public FeedOnFKeyFilter(@Nonnull HorseFollowPlugin plugin) {
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
            if (u == null || u.interactionType != InteractionType.Use) {
                continue;
            }
            String itemId = u.itemInHandId;
            boolean horseFeed = "Horse_Feed".equals(itemId);
            boolean ramFeed = "Ram_Feed".equals(itemId);
            if (!horseFeed && !ramFeed) {
                continue;
            }
            // Cancelar o packet e agendar feed na world thread
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
            final boolean feedHorse = horseFeed;
            ItemConsume itemConsume = plugin.getItemConsume();
            ItemConsume.runOnWorldThread(entityStore, () ->
                    itemConsume.tryFeedFromFKey(store, entityRef, feedHorse));
            return true;
        }
        return false;
    }
}
