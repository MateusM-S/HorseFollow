package com.horsefollow;

import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class FollowService {

    private static final boolean DEBUG = false;

    // playerRef -> horseRef
    private final Map<Ref<EntityStore>, Ref<EntityStore>> bound = new ConcurrentHashMap<>();
    // playerRef -> horse UUID (stable across entity ref changes)
    private final Map<Ref<EntityStore>, UUID> boundUuids = new ConcurrentHashMap<>();
    // playerRef -> last teleport tick
    private final Map<Ref<EntityStore>, Long> lastTeleportTickByPlayer = new ConcurrentHashMap<>();
    // playerRef -> original role index before friendly swap
    private final Map<Ref<EntityStore>, Integer> originalRoleByPlayer = new ConcurrentHashMap<>();
    // playerRefs pending friendly role swap (wait until horse is not mounted)
    private final Set<Ref<EntityStore>> pendingFriendlyRole = ConcurrentHashMap.newKeySet();
    // playerUuid -> horseUuid (persisted across restarts)
    private final Map<UUID, UUID> persistedBinds = new ConcurrentHashMap<>();
    private final Object persistLock = new Object();
    private final Path bindsFile;

    // tuning
    private static final double MAX_DISTANCE = 25.0;
    private static final double BEHIND_OFFSET = 3.0;
    private static final int MAX_TPS_PER_APPLY = 2;
    private static final int TP_COOLDOWN_TICKS = 200;
    private static final String FRIENDLY_ROLE_ID = "Horse_Friendly";

    private long tickCounter = 0L;

    public FollowService(Path dataDirectory) {
        this.bindsFile = dataDirectory != null ? dataDirectory.resolve("binds.txt") : null;
        loadPersistedBinds();
    }

    public void bind(Ref<EntityStore> playerRef, Ref<EntityStore> horseRef) {
        if (playerRef == null || horseRef == null) return;
        bound.put(playerRef, horseRef);
        UUID horseUuid = tryReadUuid(horseRef);
        if (horseUuid != null) {
            boundUuids.put(playerRef, horseUuid);
            debug("bind uuid playerRef=" + playerRef + " horseUuid=" + horseUuid);
            persistBind(playerRef, horseUuid);
        }
        debug("bind playerRef=" + playerRef + " horseRef=" + horseRef);
        if (!applyFriendlyRole(playerRef, horseRef)) {
            pendingFriendlyRole.add(playerRef);
        }
    }

    public void unbind(Ref<EntityStore> playerRef) {
        if (playerRef == null) return;
        Ref<EntityStore> horseRef = bound.get(playerRef);
        if (horseRef != null) {
            applyOriginalRole(playerRef, horseRef);
        }
        clearPersistedBind(playerRef);
        bound.remove(playerRef);
        boundUuids.remove(playerRef);
        lastTeleportTickByPlayer.remove(playerRef);
        originalRoleByPlayer.remove(playerRef);
        pendingFriendlyRole.remove(playerRef);
        debug("unbind playerRef=" + playerRef);
    }

    public boolean isBound(Ref<EntityStore> playerRef) {
        return playerRef != null && bound.containsKey(playerRef);
    }

    public Ref<EntityStore> getBoundHorse(Ref<EntityStore> playerRef) {
        if (playerRef == null) return null;
        return bound.get(playerRef);
    }

    private boolean applyFriendlyRole(Ref<EntityStore> playerRef, Ref<EntityStore> horseRef) {
        if (playerRef == null || horseRef == null) return false;
        Store<EntityStore> store = horseRef.getStore();
        if (store == null) return false;
        EntityStore entityStore = store.getExternalData();
        AtomicReference<Boolean> applied = new AtomicReference<>(false);
        worldExecute(entityStore, () -> applied.set(applyRoleChange(store, playerRef, horseRef, FRIENDLY_ROLE_ID, true)));
        return applied.get();
    }

    private void applyOriginalRole(Ref<EntityStore> playerRef, Ref<EntityStore> horseRef) {
        if (playerRef == null || horseRef == null) return;
        Store<EntityStore> store = horseRef.getStore();
        if (store == null) return;
        EntityStore entityStore = store.getExternalData();
        worldExecute(entityStore, () -> {
            Integer originalIndex = originalRoleByPlayer.get(playerRef);
            int targetIndex = originalIndex != null ? originalIndex : NPCPlugin.get().getIndex("Horse");
            if (targetIndex < 0) return;
            applyRoleChange(store, playerRef, horseRef, targetIndex, false);
        });
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
        tickCounter++;
        rebindPersistedForOnlinePlayers();
        if (bound.isEmpty()) return;
        long currentTick = tickCounter;
        AtomicInteger teleportsThisTick = new AtomicInteger(0);

        for (Map.Entry<Ref<EntityStore>, Ref<EntityStore>> e : bound.entrySet()) {
            Ref<EntityStore> playerRef = e.getKey();
            Ref<EntityStore> horseRef = e.getValue();

            if (playerRef == null || horseRef == null) continue;
            if (!playerRef.isValid()) {
                bound.remove(playerRef);
                lastTeleportTickByPlayer.remove(playerRef);
                boundUuids.remove(playerRef);
                originalRoleByPlayer.remove(playerRef);
                pendingFriendlyRole.remove(playerRef);
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
                    if (teleportsThisTick.get() >= MAX_TPS_PER_APPLY) return;
                    Long lastTick = lastTeleportTickByPlayer.get(playerRef);
                    if (lastTick != null && (currentTick - lastTick) < TP_COOLDOWN_TICKS) return;

                    if (pendingFriendlyRole.contains(playerRef)) {
                        if (applyRoleChange(store, playerRef, horseRef, FRIENDLY_ROLE_ID, true)) {
                            pendingFriendlyRole.remove(playerRef);
                        }
                    }

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
                    lastTeleportTickByPlayer.put(playerRef, currentTick);
                    teleportsThisTick.incrementAndGet();

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
                UUID resolvedUuid = tryReadUuid(resolved);
                if (resolvedUuid != null) {
                    boundUuids.put(playerRef, resolvedUuid);
                }
                applyFriendlyRole(playerRef, resolved);
            } else {
                bound.remove(playerRef);
                boundUuids.remove(playerRef);
            }
        });
    }

    private boolean applyRoleChange(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef,
            String targetRoleId,
            boolean storeOriginal
    ) {
        if (store == null || playerRef == null || horseRef == null) return false;
        if (targetRoleId == null || targetRoleId.isBlank()) return false;

        int targetIndex = NPCPlugin.get().getIndex(targetRoleId);
        if (targetIndex < 0) return false;

        return applyRoleChange(store, playerRef, horseRef, targetIndex, storeOriginal);
    }

    private boolean applyRoleChange(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef,
            int targetIndex,
            boolean storeOriginal
    ) {
        if (store == null || playerRef == null || horseRef == null) return false;
        if (targetIndex < 0) return false;

        NPCEntity npc = store.getComponent(horseRef, NPCEntity.getComponentType());
        if (npc == null) return false;
        Role role = npc.getRole();
        if (role == null) return false;

        if (storeOriginal) {
            originalRoleByPlayer.putIfAbsent(playerRef, npc.getRoleIndex());
        }

        if (npc.getRoleIndex() == targetIndex) return true;

        NPCMountComponent npcMount = store.getComponent(horseRef, NPCMountComponent.getComponentType());
        if (npcMount != null && npcMount.getOwnerPlayerRef() != null) {
            return false;
        }
        if (npcMount != null) {
            npcMount.setOriginalRoleIndex(targetIndex);
        }

        RoleChangeSystem.requestRoleChange(horseRef, role, targetIndex, false, "Idle", null, store);
        return true;
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

    private static UUID tryReadPlayerUuid(Ref<EntityStore> playerRef) {
        UUID uuid = tryReadUuid(playerRef);
        if (uuid != null) return uuid;
        if (playerRef == null) return null;
        Store<EntityStore> store = playerRef.getStore();
        if (store == null) return null;
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        return player != null ? player.getUuid() : null;
    }

    private void persistBind(Ref<EntityStore> playerRef, UUID horseUuid) {
        if (horseUuid == null) return;
        UUID playerUuid = tryReadPlayerUuid(playerRef);
        if (playerUuid == null) return;
        persistedBinds.put(playerUuid, horseUuid);
        savePersistedBinds();
    }

    private void clearPersistedBind(Ref<EntityStore> playerRef) {
        UUID playerUuid = tryReadPlayerUuid(playerRef);
        if (playerUuid == null) return;
        if (persistedBinds.remove(playerUuid) != null) {
            savePersistedBinds();
        }
    }

    private void rebindPersistedForOnlinePlayers() {
        if (persistedBinds.isEmpty()) return;
        List<PlayerRef> players = Universe.get().getPlayers();
        for (PlayerRef player : players) {
            UUID playerUuid = player.getUuid();
            UUID horseUuid = persistedBinds.get(playerUuid);
            if (horseUuid == null) continue;
            Ref<EntityStore> playerRef = player.getReference();
            if (playerRef == null || !playerRef.isValid()) continue;
            if (bound.containsKey(playerRef)) continue;
            Store<EntityStore> store = playerRef.getStore();
            if (store == null) continue;
            EntityStore entityStore = store.getExternalData();
            worldExecute(entityStore, () -> {
                Ref<EntityStore> horseRef = entityStore.getRefFromUUID(horseUuid);
                if (horseRef != null && horseRef.isValid()) {
                    bound.put(playerRef, horseRef);
                    boundUuids.put(playerRef, horseUuid);
                    if (!applyFriendlyRole(playerRef, horseRef)) {
                        pendingFriendlyRole.add(playerRef);
                    }
                }
            });
        }
    }

    private void loadPersistedBinds() {
        if (bindsFile == null) return;
        synchronized (persistLock) {
            try {
                Files.createDirectories(bindsFile.getParent());
                if (!Files.exists(bindsFile)) return;
                List<String> lines = Files.readAllLines(bindsFile, StandardCharsets.UTF_8);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    String[] parts = trimmed.split("=", 2);
                    if (parts.length != 2) continue;
                    try {
                        UUID playerUuid = UUID.fromString(parts[0].trim());
                        UUID horseUuid = UUID.fromString(parts[1].trim());
                        persistedBinds.put(playerUuid, horseUuid);
                    } catch (IllegalArgumentException ignored) {
                        // ignore malformed lines
                    }
                }
            } catch (IOException e) {
                debug("persist load failed: " + e.getMessage());
            }
        }
    }

    private void savePersistedBinds() {
        if (bindsFile == null) return;
        synchronized (persistLock) {
            try {
                Files.createDirectories(bindsFile.getParent());
                List<String> lines = new ArrayList<>();
                for (Map.Entry<UUID, UUID> entry : persistedBinds.entrySet()) {
                    lines.add(entry.getKey() + "=" + entry.getValue());
                }
                Path tmp = bindsFile.resolveSibling(bindsFile.getFileName().toString() + ".tmp");
                Files.write(tmp, lines, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, bindsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Files.move(tmp, bindsFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                debug("persist save failed: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        savePersistedBinds();
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
