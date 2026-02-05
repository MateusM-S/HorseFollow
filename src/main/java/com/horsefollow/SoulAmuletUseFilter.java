package com.horsefollow;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Intercepta Primary e Secondary com Soul_Amulet.
 * Primary = captura (cadáver de cavalo esqueleto no alcance → amuleto cheio).
 * Secondary = soltar (amuleto cheio → spawn do cavalo esqueleto com mesmo UUID, role _Friendly).
 */
public final class SoulAmuletUseFilter implements PlayerPacketFilter {

    private static final int PACKET_ID_SYNC_INTERACTION_CHAINS = 290;
    private static final String SOUL_AMULET_ITEM_ID = "Soul_Amulet";
    private static final String SOUL_AMULET_FULL_ITEM_ID = "Soul_Amulet_Full";

    private final HorseFollowPlugin plugin;

    public SoulAmuletUseFilter(@Nonnull HorseFollowPlugin plugin) {
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
            if (u == null) continue;
            if (u.interactionType != InteractionType.Primary && u.interactionType != InteractionType.Secondary) {
                continue;
            }
            String itemId = u.itemInHandId;
            if (!SOUL_AMULET_ITEM_ID.equals(itemId) && !SOUL_AMULET_FULL_ITEM_ID.equals(itemId)) {
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
            Ref<EntityStore> ref = entityRef;
            boolean isSecondary = (u.interactionType == InteractionType.Secondary);
            Vector3d targetPos = extractTargetSpawnPosition(u);
            ItemConsume.runOnWorldThread(entityStore, () -> {
                // Amuleto cheio (metadata no item ou cache da última captura) → soltar cavalo.
                ItemConsume.SoulAmuletData data = ItemConsume.getHeldSoulAmuletData(store, ref);
                if (data == null) data = SoulAmuletCapture.takeLastCapturedSoulIfHoldingAmulet(store, ref);
                if (data != null) {
                    SoulAmuletRelease.tryRelease(store, ref, data, plugin.getFollowService(), targetPos);
                    return;
                }
                if (isSecondary) {
                    return;
                }
                SoulAmuletCapture.tryCaptureOnlyIfRecentlyDead(store, ref, plugin.getItemConsume());
            });
            return false;
        }
        return false;
    }

    /** Extrai a posição do target: bloco onde mira OU entidade atingida (ex.: cavalo). */
    private static Vector3d extractTargetSpawnPosition(SyncInteractionChain chain) {
        if (chain == null) return null;
        try {
            if (chain.data != null) {
                var bp = chain.data.blockPosition;
                if (bp != null) {
                    return new Vector3d(bp.x + 0.5, bp.y + 1.0, bp.z + 0.5);
                }
                var hl = chain.data.hitLocation;
                if (hl != null) {
                    return new Vector3d(hl.x, hl.y, hl.z);
                }
            }
            if (chain.interactionData != null) {
                for (var id : chain.interactionData) {
                    if (id == null) continue;
                    if (id.hitEntities != null && id.hitEntities.length > 0) {
                        var he = id.hitEntities[0];
                        if (he.position != null) {
                            return new Vector3d(he.position.x, he.position.y, he.position.z);
                        }
                        if (he.hitLocation != null) {
                            return new Vector3d(he.hitLocation.x, he.hitLocation.y, he.hitLocation.z);
                        }
                    }
                    if (id.raycastHit != null) {
                        return new Vector3d(id.raycastHit.x, id.raycastHit.y, id.raycastHit.z);
                    }
                    if (id.blockPosition != null) {
                        var bp = id.blockPosition;
                        return new Vector3d(bp.x + 0.5, bp.y + 1.0, bp.z + 0.5);
                    }
                }
            }
        } catch (Throwable t) { }
        return null;
    }
}
