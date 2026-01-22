package com.horsefollow;

import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class FollowService {

    private static final boolean DEBUG = false;

    // playerRef -> horseRef
    private final Map<Ref<EntityStore>, Ref<EntityStore>> bound = new ConcurrentHashMap<>();
    // playerRef -> horse UUID (stable across entity ref changes)
    private final Map<Ref<EntityStore>, UUID> boundUuids = new ConcurrentHashMap<>();

    // tuning
    private static final double MAX_DISTANCE = 12.0;
    private static final double BEHIND_OFFSET = 2.0;

    public void bind(Ref<EntityStore> playerRef, Ref<EntityStore> horseRef) {
        if (playerRef == null || horseRef == null) return;
        bound.put(playerRef, horseRef);
        UUID horseUuid = tryReadUuid(horseRef);
        if (horseUuid != null) {
            boundUuids.put(playerRef, horseUuid);
            debug("bind uuid playerRef=" + playerRef + " horseUuid=" + horseUuid);
        }
        debug("bind playerRef=" + playerRef + " horseRef=" + horseRef);
    }

    public void unbind(Ref<EntityStore> playerRef) {
        if (playerRef == null) return;
        bound.remove(playerRef);
        boundUuids.remove(playerRef);
        debug("unbind playerRef=" + playerRef);
    }

    public boolean isBound(Ref<EntityStore> playerRef) {
        return playerRef != null && bound.containsKey(playerRef);
    }

    public Ref<EntityStore> getBoundHorse(Ref<EntityStore> playerRef) {
        if (playerRef == null) return null;
        return bound.get(playerRef);
    }

    /**
     * Roda um runnable na thread do world do store associado ao ref.
     * (Uso: comandos e qualquer acesso a componentes/ecs)
     */
    public static void runOnWorld(Ref<EntityStore> anyRef, Runnable r) {
        if (anyRef == null || r == null) return;
        try {
            Store<EntityStore> store = anyRef.getStore();
            if (store == null) {
                r.run();
                return;
            }
            EntityStore entityStore = store.getExternalData();
            worldExecute(entityStore, r);
        } catch (Throwable t) {
            // fallback
            r.run();
        }
    }

    public void tick() {
        if (bound.isEmpty()) return;

        for (Map.Entry<Ref<EntityStore>, Ref<EntityStore>> e : bound.entrySet()) {
            Ref<EntityStore> playerRef = e.getKey();
            Ref<EntityStore> horseRef = e.getValue();

            if (playerRef == null || horseRef == null) continue;
            if (!playerRef.isValid()) {
                bound.remove(playerRef);
                continue;
            }
            if (!horseRef.isValid()) {
                debug("horseRef invalid for playerRef=" + playerRef + " horseRef=" + horseRef);
                tryRebind(playerRef);
                continue;
            }

            // usa o store do horseRef (normalmente é o mesmo store/mundo do playerRef)
            Store<EntityStore> store = horseRef.getStore();
            EntityStore entityStore = store.getExternalData();

            // IMPORTANTÍSSIMO: acesso ECS dentro do world.execute(...)
            worldExecute(entityStore, () -> {
                try {
                    TransformComponent playerTf = store.getComponent(playerRef, TransformComponent.getComponentType());
                    TransformComponent horseTf  = store.getComponent(horseRef, TransformComponent.getComponentType());
                    if (playerTf == null || horseTf == null) return;

                    Vector3d p = playerTf.getPosition();
                    Vector3d h = horseTf.getPosition();
                    if (p == null || h == null) return;

                    double dx = p.getX() - h.getX();
                    double dy = p.getY() - h.getY();
                    double dz = p.getZ() - h.getZ();
                    double distSq = dx * dx + dy * dy + dz * dz;

                    if (distSq <= (MAX_DISTANCE * MAX_DISTANCE)) return;

                    Vector3d target = new Vector3d(p.getX(), p.getY(), p.getZ() - BEHIND_OFFSET);
                    horseTf.teleportPosition(target);

                } catch (Throwable ignore) {
                    // sem log
                }
            });
        }
    }

    private void tryRebind(Ref<EntityStore> playerRef) {
        Store<EntityStore> store = playerRef.getStore();
        if (store == null) {
            debug("rebind store null playerRef=" + playerRef);
            bound.remove(playerRef);
            return;
        }
        EntityStore entityStore = store.getExternalData();
        worldExecute(entityStore, () -> {
            Ref<EntityStore> resolved = resolveByUuid(store, playerRef);
            if (resolved == null) {
                resolved = resolveHorseFromPlayer(store, playerRef);
            }
            debug("rebind resolved playerRef=" + playerRef + " resolved=" + resolved);
            if (resolved != null && resolved.isValid()) {
                bound.put(playerRef, resolved);
            } else {
                bound.remove(playerRef);
                boundUuids.remove(playerRef);
            }
        });
    }

    private Ref<EntityStore> resolveByUuid(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        UUID uuid = boundUuids.get(playerRef);
        if (uuid == null) return null;
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null) return null;
        Ref<EntityStore> ref = entityStore.getRefFromUUID(uuid);
        debug("resolve by uuid playerRef=" + playerRef + " horseUuid=" + uuid + " ref=" + ref);
        if (ref != null && ref.isValid()) {
            return ref;
        }
        return null;
    }

    private static Ref<EntityStore> resolveHorseFromPlayer(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        MountedComponent mounted = store.getComponent(playerRef, MountedComponent.getComponentType());
        if (mounted != null) {
            Ref<EntityStore> mountedTo = mounted.getMountedToEntity();
            debug("resolve mounted component mountedTo=" + mountedTo);
            if (mountedTo != null && mountedTo.isValid()) {
                return mountedTo;
            }
        } else {
            debug("resolve mounted component missing for playerRef=" + playerRef);
        }
        Ref<EntityStore> byPassenger = findMountFromPassengers(store, playerRef);
        if (byPassenger != null) return byPassenger;
        return findNpcMountByOwner(store, playerRef);
    }

    private static Ref<EntityStore> findMountFromPassengers(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null) return null;
        PlayerRef playerObj = store.getComponent(playerRef, PlayerRef.getComponentType());
        AtomicReference<Ref<EntityStore>> found = new AtomicReference<>();
        Archetype<EntityStore> query = Archetype.of(MountedByComponent.getComponentType());
        store.forEachChunk(query, (chunk, cb) -> {
            if (found.get() != null) return;
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                MountedByComponent mountedBy = chunk.getComponent(i, MountedByComponent.getComponentType());
                if (mountedBy == null) continue;
                List<Ref<EntityStore>> passengers = mountedBy.getPassengers();
                if (passengers == null) continue;
                for (Ref<EntityStore> passenger : passengers) {
                    if (playerRef.equals(passenger)) {
                        found.set(chunk.getReferenceTo(i));
                        debug("resolve found via passengers mountRef=" + found.get());
                        return;
                    }
                    if (playerObj != null) {
                        PlayerRef passengerObj = store.getComponent(passenger, PlayerRef.getComponentType());
                        if (passengerObj != null && passengerObj.getUuid().equals(playerObj.getUuid())) {
                            found.set(chunk.getReferenceTo(i));
                            debug("resolve found via passenger uuid mountRef=" + found.get());
                            return;
                        }
                    }
                }
            }
        });
        return found.get();
    }

    private static Ref<EntityStore> findNpcMountByOwner(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null) return null;
        PlayerRef playerObj = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerObj == null) return null;
        AtomicReference<Ref<EntityStore>> found = new AtomicReference<>();
        Archetype<EntityStore> query = Archetype.of(NPCMountComponent.getComponentType());
        store.forEachChunk(query, (chunk, cb) -> {
            if (found.get() != null) return;
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                NPCMountComponent npcMount = chunk.getComponent(i, NPCMountComponent.getComponentType());
                if (npcMount == null) continue;
                PlayerRef owner = npcMount.getOwnerPlayerRef();
                if (owner != null && owner.getUuid().equals(playerObj.getUuid())) {
                    found.set(chunk.getReferenceTo(i));
                    debug("resolve found via npc mount owner mountRef=" + found.get());
                    return;
                }
            }
        });
        return found.get();
    }

    private static UUID tryReadUuid(Ref<EntityStore> entityRef) {
        if (entityRef == null) return null;
        Store<EntityStore> store = entityRef.getStore();
        if (store == null) return null;
        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }

    private static void debug(String message) {
        if (DEBUG) {
            System.out.println("[HorseFollow] " + message);
        }
    }

    /**
     * Chama world.execute(runnable) sem depender dos nomes exatos,
     * evitando crash por "async component access".
     */
    private static void worldExecute(Object entityStore, Runnable r) {
        try {
            Object world = null;

            // tenta getWorld()
            try {
                Method m = entityStore.getClass().getMethod("getWorld");
                world = m.invoke(entityStore);
            } catch (NoSuchMethodException ignored) {}

            // tenta world()
            if (world == null) {
                try {
                    Method m = entityStore.getClass().getMethod("world");
                    world = m.invoke(entityStore);
                } catch (NoSuchMethodException ignored) {}
            }

            if (world == null) {
                // fallback (pode ser async perigoso, mas evita “não faz nada”)
                r.run();
                return;
            }

            // tenta execute(Runnable)
            Method exec = world.getClass().getMethod("execute", Runnable.class);
            exec.invoke(world, r);

        } catch (Throwable t) {
            // fallback
            r.run();
        }
    }
}
