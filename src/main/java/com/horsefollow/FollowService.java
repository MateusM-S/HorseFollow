package com.horsefollow;

import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.flock.FlockMembership;
import com.hypixel.hytale.server.flock.FlockMembershipSystems;
import com.hypixel.hytale.server.flock.FlockPlugin;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
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
import java.time.Instant;
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
    // playerRefs pending stay request (wait until horse is not mounted)
    // value = anchor position; when null, anchor is captured at apply-time (after dismount)
    private final Map<Ref<EntityStore>, Vector3d> pendingStayAnchorByPlayer = new ConcurrentHashMap<>();

    // playerRef -> stay state (anchor position, horse uuid, last damage time snapshot)
    private final Map<Ref<EntityStore>, StayState> stayByPlayer = new ConcurrentHashMap<>();
    // playerUuid -> horseUuid (persisted across restarts)
    private final Map<UUID, UUID> persistedBinds = new ConcurrentHashMap<>();
    // playerUuid -> persisted stay (horseUuid + anchor)
    private final Map<UUID, PersistedStay> persistedStay = new ConcurrentHashMap<>();
    private final Object persistLock = new Object();
    private final Path bindsFile;
    private final Path stayFile;

    // tuning
    private static final String FRIENDLY_SUFFIX = "_Friendly";
    private static final double STAY_MAX_DRIFT_BLOCKS = 0.5;
    private static final double STAY_MAX_DRIFT_SQ = STAY_MAX_DRIFT_BLOCKS * STAY_MAX_DRIFT_BLOCKS;

    private long tickCounter = 0L;
    private final AtomicReference<FollowConfig> configRef = new AtomicReference<>(FollowConfig.defaults());
    private final Path configFile;

    private static final class StayState {
        private final UUID horseUuid;
        private final Vector3d anchor;
        private final Instant lastDamageTimeAtSet;

        private StayState(UUID horseUuid, Vector3d anchor, Instant lastDamageTimeAtSet) {
            this.horseUuid = horseUuid;
            this.anchor = anchor;
            this.lastDamageTimeAtSet = lastDamageTimeAtSet;
        }
    }

    private static final class PersistedStay {
        private final UUID horseUuid;
        private final Vector3d anchor;

        private PersistedStay(UUID horseUuid, Vector3d anchor) {
            this.horseUuid = horseUuid;
            this.anchor = anchor;
        }
    }

    public enum StayRequestResult {
        APPLIED,
        QUEUED,
        FAIL
    }

    public FollowService(Path dataDirectory) {
        this.bindsFile = dataDirectory != null ? dataDirectory.resolve("binds.txt") : null;
        this.stayFile = dataDirectory != null ? dataDirectory.resolve("stay.txt") : null;
        this.configFile = dataDirectory != null ? dataDirectory.resolve("config.properties") : null;
        reloadConfig();
        loadPersistedBinds();
        loadPersistedStay();
    }

    public void bind(Ref<EntityStore> playerRef, Ref<EntityStore> horseRef) {
        if (playerRef == null || horseRef == null) return;

        // garante 1 vinculo por player: se ja existe outro, desvincula (remove follow/role/flock do anterior)
        Ref<EntityStore> previous = bound.get(playerRef);
        if (previous != null && previous.isValid() && !previous.equals(horseRef)) {
            unbind(playerRef);
        }

        bound.put(playerRef, horseRef);
        // novo bind: garante que não fica em "stay" do vínculo anterior
        stayByPlayer.remove(playerRef);
        pendingStayAnchorByPlayer.remove(playerRef);
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
        Store<EntityStore> store = horseRef.getStore();
        if (store != null) {
            EntityStore entityStore = store.getExternalData();
            worldExecute(entityStore, () -> {
                // evita conflito com montaria: só ativa flock-follow quando desmontado
                UUID playerUuid = tryReadPlayerUuid(playerRef);
                PersistedStay ps = (playerUuid != null) ? persistedStay.get(playerUuid) : null;
                boolean hasPersistedStayForThisHorse = ps != null
                        && ps.horseUuid != null
                        && ps.horseUuid.equals(tryReadUuid(horseRef))
                        && ps.anchor != null;

                if (hasPersistedStayForThisHorse) {
                    // Se entrou com stay persistido, aplica (ou deixa pendente se estiver montado).
                    if (isPlayerMountedOnHorse(store, playerRef, horseRef)) {
                        pendingStayAnchorByPlayer.put(playerRef, ps.anchor);
                        store.tryRemoveComponent(horseRef, FlockMembership.getComponentType());
                        return;
                    }
                    applyStayAtAnchor(store, playerRef, horseRef, ps.anchor);
                    return;
                }

                if (isPlayerMountedOnHorse(store, playerRef, horseRef)) {
                    store.tryRemoveComponent(horseRef, FlockMembership.getComponentType());
                    return;
                }
                ensureFollowFlock(store, playerRef, horseRef);
            });
        }
    }

    public void unbind(Ref<EntityStore> playerRef) {
        if (playerRef == null) return;
        Ref<EntityStore> horseRef = bound.get(playerRef);
        if (horseRef != null) {
            applyOriginalRole(playerRef, horseRef);
            Store<EntityStore> store = horseRef.getStore();
            if (store != null) {
                EntityStore entityStore = store.getExternalData();
                worldExecute(entityStore, () ->
                        store.tryRemoveComponent(horseRef, FlockMembership.getComponentType()));
            }
        }
        stayByPlayer.remove(playerRef);
        pendingStayAnchorByPlayer.remove(playerRef);
        clearPersistedStay(playerRef);
        clearPersistedBind(playerRef);
        bound.remove(playerRef);
        boundUuids.remove(playerRef);
        lastTeleportTickByPlayer.remove(playerRef);
        originalRoleByPlayer.remove(playerRef);
        pendingFriendlyRole.remove(playerRef);
        debug("unbind playerRef=" + playerRef);
    }

    public boolean isStaying(Ref<EntityStore> playerRef) {
        return playerRef != null && stayByPlayer.containsKey(playerRef);
    }

    /**
     * Solicita "stay": desliga follow e prende a montaria no ponto.
     * Se o player estiver montado, agenda para aplicar quando desmontar.
     *
     * Retorna:
     * - true  => stay aplicado imediatamente
     * - false => stay ficou pendente (ex.: montado) ou falhou (sem vínculo/refs inválidos)
     */
    public StayRequestResult requestStay(Ref<EntityStore> playerRef) {
        // IMPORTANTE: este método deve ser chamado dentro da WorldThread (ex.: via Command worldExecute).
        // Se ele for chamado fora da world thread, a API pode lançar erros de acesso async a componentes.
        try {
            if (playerRef == null) return StayRequestResult.FAIL;
            Ref<EntityStore> horseRef = bound.get(playerRef);
            if (horseRef == null || !horseRef.isValid()) return StayRequestResult.FAIL;
            Store<EntityStore> store = horseRef.getStore();
            if (store == null) return StayRequestResult.FAIL;

            if (isPlayerMountedOnHorse(store, playerRef, horseRef)) {
                // pendente: aplica quando desmontar, capturando o ponto no momento da aplicação
                pendingStayAnchorByPlayer.put(playerRef, null);
                return StayRequestResult.QUEUED;
            }
            return applyStayNow(store, playerRef, horseRef) ? StayRequestResult.APPLIED : StayRequestResult.FAIL;
        } catch (Throwable ignored) {
            return StayRequestResult.FAIL;
        }
    }

    /**
     * Sai do "stay" e volta ao comportamento normal de follow (quando desmontado).
     */
    public void clearStay(Ref<EntityStore> playerRef) {
        if (playerRef == null) return;
        stayByPlayer.remove(playerRef);
        pendingStayAnchorByPlayer.remove(playerRef);
        clearPersistedStay(playerRef);
    }

    public boolean isBound(Ref<EntityStore> playerRef) {
        return playerRef != null && bound.containsKey(playerRef);
    }

    /**
     * Retorna uma cópia do mapa de players vinculados para uso externo (ex: ItemConsume).
     * Não modifica o mapa original.
     */
    public Map<Ref<EntityStore>, Ref<EntityStore>> getBoundPlayers() {
        return new java.util.HashMap<>(bound);
    }

    public Ref<EntityStore> getBoundHorse(Ref<EntityStore> playerRef) {
        if (playerRef == null) return null;
        return bound.get(playerRef);
    }

    public FollowConfig getConfig() {
        return configRef.get();
    }

    public void reloadConfig() {
        FollowConfig config = FollowConfig.load(configFile);
        configRef.set(config);
    }

    public boolean updateTeleportDistance(double value) {
        if (value < 0) return false;
        FollowConfig updated = configRef.get().withTeleportDistance(value);
        configRef.set(updated);
        updated.save(configFile);
        return true;
    }

    private boolean applyFriendlyRole(Ref<EntityStore> playerRef, Ref<EntityStore> horseRef) {
        if (playerRef == null || horseRef == null) return false;
        Store<EntityStore> store = horseRef.getStore();
        if (store == null) return false;
        EntityStore entityStore = store.getExternalData();
        AtomicReference<Boolean> applied = new AtomicReference<>(false);
        worldExecute(entityStore, () -> applied.set(tryApplyFriendlyRoleNow(store, playerRef, horseRef, true)));
        return applied.get();
    }

    private boolean tryApplyFriendlyRoleNow(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef,
            boolean storeOriginal
    ) {
        if (store == null || playerRef == null || horseRef == null) return false;
        String friendlyId = resolveFriendlyRoleId(store, playerRef, horseRef);
        if (friendlyId == null) {
            // Não existe role _Friendly para esta entidade. Não deixa pendente.
            return true;
        }
        return applyRoleChange(store, playerRef, horseRef, friendlyId, storeOriginal);
    }

    private String resolveFriendlyRoleId(Store<EntityStore> store, Ref<EntityStore> playerRef, Ref<EntityStore> horseRef) {
        String base = resolveBaseRoleName(store, playerRef, horseRef);
        if (base == null) return null;
        String candidate = base + FRIENDLY_SUFFIX;
        int idx = NPCPlugin.get().getIndex(candidate);
        return idx >= 0 ? candidate : null;
    }

    /**
     * Obtém o nome base do role da montaria (ex.: "Horse", "Ram") para derivar o _Friendly.
     * Sempre usa o role atual da entidade horseRef; não usa originalRoleByPlayer, para evitar
     * que ao trocar de montaria (cavalo -> carneiro) sem unbind o carneiro receba Horse_Friendly.
     */
    private String resolveBaseRoleName(Store<EntityStore> store, Ref<EntityStore> playerRef, Ref<EntityStore> horseRef) {
        if (store == null || horseRef == null) return null;

        NPCEntity npc = store.getComponent(horseRef, NPCEntity.getComponentType());
        if (npc == null) return null;
        int roleIndex = npc.getRoleIndex();
        if (roleIndex < 0) return null;

        String name = NPCPlugin.get().getName(roleIndex);
        if (name == null || name.isBlank()) return null;
        if (name.endsWith(FRIENDLY_SUFFIX)) {
            return name.substring(0, name.length() - FRIENDLY_SUFFIX.length());
        }
        return name;
    }

    private void applyOriginalRole(Ref<EntityStore> playerRef, Ref<EntityStore> horseRef) {
        if (playerRef == null || horseRef == null) return;
        Store<EntityStore> store = horseRef.getStore();
        if (store == null) return;
        EntityStore entityStore = store.getExternalData();
        worldExecute(entityStore, () -> {
            Integer originalIndex = originalRoleByPlayer.get(playerRef);
            int targetIndex;
            if (originalIndex != null) {
                targetIndex = originalIndex;
            } else {
                // fallback: restaura para o role base do próprio NPC (Horse/Ram/...)
                String baseName = resolveBaseRoleName(store, playerRef, horseRef);
                targetIndex = baseName != null ? NPCPlugin.get().getIndex(baseName) : -1;
            }
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
        FollowConfig config = configRef.get();
        double maxDistance = config.getTeleportDistance();
        double behindOffset = config.getBehindOffset();
        int maxTeleportsPerTick = config.getMaxTeleportsPerTick();
        int tpCooldownTicks = config.getTpCooldownTicks();

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
                pendingStayAnchorByPlayer.remove(playerRef);
                stayByPlayer.remove(playerRef);
                continue;
            }
            if (!horseRef.isValid()) {
                debug("horseRef invalid for playerRef=" + playerRef + " horseRef=" + horseRef);
                tryRebind(playerRef);
                continue;
            }

            // usa o store do horseRef (normalmente é o mesmo store/mundo do playerRef)
            Store<EntityStore> store = horseRef.getStore();
            if (store == null) continue;
            EntityStore entityStore = store.getExternalData();

            // IMPORTANTÍSSIMO: acesso ECS dentro do world.execute(...)
            worldExecute(entityStore, () -> {
                try {
                    // Se o player pediu "stay" enquanto estava montado, aplica quando desmontar.
                    if (pendingStayAnchorByPlayer.containsKey(playerRef)) {
                        if (!isPlayerMountedOnHorse(store, playerRef, horseRef)) {
                            Vector3d anchor = pendingStayAnchorByPlayer.get(playerRef);
                            if (anchor != null) {
                                applyStayAtAnchor(store, playerRef, horseRef, anchor);
                            } else {
                                applyStayNow(store, playerRef, horseRef);
                            }
                            pendingStayAnchorByPlayer.remove(playerRef);
                        }
                    }

                    // Se está em "stay", prende no ponto e só sai por: dano, call (via clearStay), ou montar.
                    StayState stay = stayByPlayer.get(playerRef);
                    if (stay != null) {
                        // Se o bind/rebind mudou a entidade (uuid diferente), cancela o stay para não prender o NPC errado.
                        if (stay.horseUuid != null) {
                            UUID currentUuid = tryReadUuid(horseRef);
                            if (currentUuid != null && !currentUuid.equals(stay.horseUuid)) {
                                stayByPlayer.remove(playerRef);
                                pendingStayAnchorByPlayer.remove(playerRef);
                                clearPersistedStay(playerRef);
                                // continua fluxo normal
                                stay = null;
                            }
                        }

                        if (stay == null) {
                            // cai no fluxo normal
                        } else {
                        // Se montou, o stay deve sair imediatamente
                        if (isPlayerMountedOnHorse(store, playerRef, horseRef)) {
                            stayByPlayer.remove(playerRef);
                            pendingStayAnchorByPlayer.remove(playerRef);
                            clearPersistedStay(playerRef);
                            store.tryRemoveComponent(horseRef, FlockMembership.getComponentType());
                            return;
                        }

                        // Mesmo em stay, tente aplicar role amigável pendente (não depende de follow)
                        if (pendingFriendlyRole.contains(playerRef)) {
                        if (tryApplyFriendlyRoleNow(store, playerRef, horseRef, true)) {
                            pendingFriendlyRole.remove(playerRef);
                        }
                        }

                        // Se o cavalo tomou dano desde que o stay foi setado, sai do stay.
                        DamageDataComponent dmg = store.getComponent(horseRef, DamageDataComponent.getComponentType());
                        Instant lastDamage = (dmg != null) ? dmg.getLastDamageTime() : null;
                        if (lastDamage != null && stay.lastDamageTimeAtSet != null && lastDamage.isAfter(stay.lastDamageTimeAtSet)) {
                            stayByPlayer.remove(playerRef);
                            clearPersistedStay(playerRef);
                            forceNpcIdleState(store, horseRef);
                            // continua fluxo normal (follow/teleporte) neste tick
                        } else if (lastDamage != null && stay.lastDamageTimeAtSet == null) {
                            // se não tínhamos snapshot e agora tem, considera que houve "atividade" e libera
                            stayByPlayer.remove(playerRef);
                            clearPersistedStay(playerRef);
                            forceNpcIdleState(store, horseRef);
                        } else {
                            // mantém parado: remove flock-follow e teleporta de volta se driftar
                            store.tryRemoveComponent(horseRef, FlockMembership.getComponentType());
                            TransformComponent horseTf = store.getComponent(horseRef, TransformComponent.getComponentType());
                            if (horseTf != null) {
                                Vector3d h = horseTf.getPosition();
                                Vector3d a = stay.anchor;
                                if (h != null && a != null) {
                                    double dx = h.getX() - a.getX();
                                    double dy = h.getY() - a.getY();
                                    double dz = h.getZ() - a.getZ();
                                    double distSq = dx * dx + dy * dy + dz * dz;
                                    if (distSq > STAY_MAX_DRIFT_SQ) {
                                        horseTf.teleportPosition(a);
                                    }
                                }
                            }
                            return; // stay ativo: não faz follow nem teleporte por distância
                        }
                        }
                    }

                    // evita conflito com montaria: quando montado, remove flock do cavalo (desliga follow)
                    if (isPlayerMountedOnHorse(store, playerRef, horseRef)) {
                        store.tryRemoveComponent(horseRef, FlockMembership.getComponentType());
                        return;
                    }

                    // garante que (desmontado) existe flock e o player é o líder
                    ensureFollowFlock(store, playerRef, horseRef);

                    if (teleportsThisTick.get() >= maxTeleportsPerTick) return;
                    Long lastTick = lastTeleportTickByPlayer.get(playerRef);
                    if (lastTick != null && (currentTick - lastTick) < tpCooldownTicks) return;

                    if (pendingFriendlyRole.contains(playerRef)) {
                        if (tryApplyFriendlyRoleNow(store, playerRef, horseRef, true)) {
                            pendingFriendlyRole.remove(playerRef);
                        }
                    }

                    TransformComponent playerTf = store.getComponent(playerRef, TransformComponent.getComponentType());
                    TransformComponent horseTf  = store.getComponent(horseRef, TransformComponent.getComponentType());
                    if (playerTf == null || horseTf == null) return;

                    Vector3d p = playerTf.getPosition();
                    Vector3d h = horseTf.getPosition();
                    if (p == null || h == null) return;

                    if (maxDistance <= 0) return;

                    double dx = p.getX() - h.getX();
                    double dy = p.getY() - h.getY();
                    double dz = p.getZ() - h.getZ();
                    double distSq = dx * dx + dy * dy + dz * dz;

                    if (distSq <= (maxDistance * maxDistance)) return;

                    Vector3d target = new Vector3d(p.getX(), p.getY(), p.getZ() - behindOffset);
                    horseTf.teleportPosition(target);
                    lastTeleportTickByPlayer.put(playerRef, currentTick);
                    teleportsThisTick.incrementAndGet();

                } catch (Throwable ignore) {
                    // sem log
                }
            });
        }
    }

    public boolean teleportHorseNearPlayer(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef
    ) {
        if (store == null || playerRef == null || horseRef == null) return false;
        // chamar a montaria cancela o "stay"
        stayByPlayer.remove(playerRef);
        pendingStayAnchorByPlayer.remove(playerRef);
        clearPersistedStay(playerRef);
        forceNpcIdleState(store, horseRef);
        TransformComponent playerTf = store.getComponent(playerRef, TransformComponent.getComponentType());
        TransformComponent horseTf = store.getComponent(horseRef, TransformComponent.getComponentType());
        if (playerTf == null || horseTf == null) return false;
        Vector3d p = playerTf.getPosition();
        if (p == null) return false;
        FollowConfig config = configRef.get();
        Vector3d target = new Vector3d(p.getX(), p.getY(), p.getZ() - config.getBehindOffset());
        horseTf.teleportPosition(target);
        lastTeleportTickByPlayer.put(playerRef, tickCounter);
        // depois de chamar, reativa follow (se não estiver montado)
        if (!isPlayerMountedOnHorse(store, playerRef, horseRef)) {
            ensureFollowFlock(store, playerRef, horseRef);
        } else {
            store.tryRemoveComponent(horseRef, FlockMembership.getComponentType());
        }
        return true;
    }

    private static void forceNpcIdleState(Store<EntityStore> store, Ref<EntityStore> npcRef) {
        if (store == null || npcRef == null) return;
        try {
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc == null) return;
            Role role = npc.getRole();
            if (role == null) return;
            int index = npc.getRoleIndex();
            if (index < 0) return;
            // Reforça estado "Idle" para tentar limpar estados/animações (ex.: após stay/call)
            RoleChangeSystem.requestRoleChange(npcRef, role, index, false, "Idle", null, store);
        } catch (Throwable ignored) {
            // best effort
        }
    }

    private boolean applyStayNow(Store<EntityStore> store, Ref<EntityStore> playerRef, Ref<EntityStore> horseRef) {
        if (store == null || playerRef == null || horseRef == null) return false;
        TransformComponent horseTf = store.getComponent(horseRef, TransformComponent.getComponentType());
        if (horseTf == null) return false;
        Vector3d pos = horseTf.getPosition();
        if (pos == null) return false;

        // snapshot do último dano (para sair do stay ao tomar dano)
        DamageDataComponent dmg = store.getComponent(horseRef, DamageDataComponent.getComponentType());
        Instant lastDamage = (dmg != null) ? dmg.getLastDamageTime() : null;

        UUID horseUuid = tryReadUuid(horseRef);
        Vector3d anchor = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
        stayByPlayer.put(playerRef, new StayState(horseUuid, anchor, lastDamage));
        persistStay(playerRef, horseUuid, anchor);

        // desliga follow
        store.tryRemoveComponent(horseRef, FlockMembership.getComponentType());
        return true;
    }

    private boolean applyStayAtAnchor(Store<EntityStore> store, Ref<EntityStore> playerRef, Ref<EntityStore> horseRef, Vector3d anchor) {
        if (store == null || playerRef == null || horseRef == null || anchor == null) return false;
        // snapshot do último dano (para sair do stay ao tomar dano)
        DamageDataComponent dmg = store.getComponent(horseRef, DamageDataComponent.getComponentType());
        Instant lastDamage = (dmg != null) ? dmg.getLastDamageTime() : null;
        UUID horseUuid = tryReadUuid(horseRef);
        Vector3d fixed = new Vector3d(anchor.getX(), anchor.getY(), anchor.getZ());
        stayByPlayer.put(playerRef, new StayState(horseUuid, fixed, lastDamage));
        persistStay(playerRef, horseUuid, fixed);

        TransformComponent horseTf = store.getComponent(horseRef, TransformComponent.getComponentType());
        if (horseTf != null) {
            horseTf.teleportPosition(fixed);
        }
        store.tryRemoveComponent(horseRef, FlockMembership.getComponentType());
        return true;
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
                // se estiver em stay, não reativa follow automaticamente
                if (stayByPlayer.containsKey(playerRef)) {
                    store.tryRemoveComponent(resolved, FlockMembership.getComponentType());
                } else {
                    ensureFollowFlock(store, playerRef, resolved);
                }
            } else {
                bound.remove(playerRef);
                boundUuids.remove(playerRef);
                pendingStayAnchorByPlayer.remove(playerRef);
                stayByPlayer.remove(playerRef);
                clearPersistedStay(playerRef);
            }
        });
    }

    private void persistStay(Ref<EntityStore> playerRef, UUID horseUuid, Vector3d anchor) {
        if (horseUuid == null || anchor == null) return;
        UUID playerUuid = tryReadPlayerUuid(playerRef);
        if (playerUuid == null) return;
        persistedStay.put(playerUuid, new PersistedStay(horseUuid, anchor));
        savePersistedStay();
    }

    private void clearPersistedStay(Ref<EntityStore> playerRef) {
        UUID playerUuid = tryReadPlayerUuid(playerRef);
        if (playerUuid == null) return;
        if (persistedStay.remove(playerUuid) != null) {
            savePersistedStay();
        }
    }

    private void loadPersistedStay() {
        if (stayFile == null) return;
        synchronized (persistLock) {
            try {
                Files.createDirectories(stayFile.getParent());
                if (!Files.exists(stayFile)) return;
                List<String> lines = Files.readAllLines(stayFile, StandardCharsets.UTF_8);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    // format: playerUuid=horseUuid@x,y,z
                    String[] parts = trimmed.split("=", 2);
                    if (parts.length != 2) continue;
                    UUID playerUuid;
                    try {
                        playerUuid = UUID.fromString(parts[0].trim());
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    String rhs = parts[1].trim();
                    String[] stayParts = rhs.split("@", 2);
                    if (stayParts.length != 2) continue;
                    UUID horseUuid;
                    try {
                        horseUuid = UUID.fromString(stayParts[0].trim());
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    String[] xyz = stayParts[1].split(",", 3);
                    if (xyz.length != 3) continue;
                    double x = Double.parseDouble(xyz[0].trim());
                    double y = Double.parseDouble(xyz[1].trim());
                    double z = Double.parseDouble(xyz[2].trim());
                    persistedStay.put(playerUuid, new PersistedStay(horseUuid, new Vector3d(x, y, z)));
                }
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    private void savePersistedStay() {
        if (stayFile == null) return;
        synchronized (persistLock) {
            try {
                Files.createDirectories(stayFile.getParent());
                List<String> lines = new ArrayList<>();
                for (Map.Entry<UUID, PersistedStay> entry : persistedStay.entrySet()) {
                    PersistedStay ps = entry.getValue();
                    if (ps == null || ps.horseUuid == null || ps.anchor == null) continue;
                    lines.add(entry.getKey()
                            + "=" + ps.horseUuid
                            + "@" + ps.anchor.getX() + "," + ps.anchor.getY() + "," + ps.anchor.getZ());
                }
                Path tmp = stayFile.resolveSibling(stayFile.getFileName().toString() + ".tmp");
                Files.write(tmp, lines, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, stayFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Files.move(tmp, stayFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {
                // best effort
            }
        }
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
                    PersistedStay ps = persistedStay.get(playerUuid);
                    boolean hasStay = ps != null && horseUuid.equals(ps.horseUuid) && ps.anchor != null;
                    if (hasStay) {
                        if (isPlayerMountedOnHorse(store, playerRef, horseRef)) {
                            pendingStayAnchorByPlayer.put(playerRef, ps.anchor);
                            store.tryRemoveComponent(horseRef, FlockMembership.getComponentType());
                        } else {
                            applyStayAtAnchor(store, playerRef, horseRef, ps.anchor);
                        }
                    } else {
                        ensureFollowFlock(store, playerRef, horseRef);
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
        savePersistedStay();
    }

    private static void ensureFollowFlock(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef
    ) {
        if (store == null || playerRef == null || horseRef == null) return;
        NPCEntity npc = store.getComponent(horseRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) return;

        FlockMembership playerMembership = store.getComponent(playerRef, FlockMembership.getComponentType());
        Ref<EntityStore> playerFlockRef = playerMembership != null ? playerMembership.getFlockRef() : null;
        if (playerFlockRef != null && !playerFlockRef.isValid()) {
            playerFlockRef = null;
        }

        FlockMembership horseMembership = store.getComponent(horseRef, FlockMembership.getComponentType());
        Ref<EntityStore> horseFlockRef = horseMembership != null ? horseMembership.getFlockRef() : null;
        if (horseFlockRef != null && !horseFlockRef.isValid()) {
            horseFlockRef = null;
        }

        if (playerFlockRef != null && playerFlockRef.equals(horseFlockRef)) {
            // já estão no mesmo flock; tenta reforçar papeis (leader/member)
            ensureFlockRoles(store, playerRef, horseRef, playerFlockRef);
            return;
        }

        Ref<EntityStore> targetFlockRef = playerFlockRef != null ? playerFlockRef : horseFlockRef;
        if (targetFlockRef == null) {
            targetFlockRef = FlockPlugin.createFlock(store, npc.getRole());
        }
        if (targetFlockRef == null) return;

        if (playerFlockRef == null || !targetFlockRef.equals(playerFlockRef)) {
            FlockMembershipSystems.join(playerRef, targetFlockRef, store);
        }
        if (horseFlockRef == null || !targetFlockRef.equals(horseFlockRef)) {
            FlockMembershipSystems.join(horseRef, targetFlockRef, store);
        }

        ensureFlockRoles(store, playerRef, horseRef, targetFlockRef);
    }

    private static void ensureFlockRoles(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef,
            Ref<EntityStore> flockRef
    ) {
        if (store == null || playerRef == null || horseRef == null || flockRef == null) return;

        FlockMembership pm = store.getComponent(playerRef, FlockMembership.getComponentType());
        if (pm != null && flockRef.equals(pm.getFlockRef())) {
            pm.setMembershipType(FlockMembership.Type.LEADER);
        }

        FlockMembership hm = store.getComponent(horseRef, FlockMembership.getComponentType());
        if (hm != null && flockRef.equals(hm.getFlockRef())) {
            hm.setMembershipType(FlockMembership.Type.MEMBER);
        }
    }

    private static boolean isPlayerMountedOnHorse(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef
    ) {
        if (store == null || playerRef == null || horseRef == null) return false;

        // tenta comparar por UUID (refs podem mudar)
        PlayerRef playerObj = store.getComponent(playerRef, PlayerRef.getComponentType());

        // via NPCMountComponent no cavalo (owner)
        NPCMountComponent npcMount = store.getComponent(horseRef, NPCMountComponent.getComponentType());
        if (npcMount != null && playerObj != null) {
            PlayerRef owner = npcMount.getOwnerPlayerRef();
            if (owner != null && owner.getUuid().equals(playerObj.getUuid())) {
                return true;
            }
        }

        // via MountedComponent no player
        MountedComponent mounted = store.getComponent(playerRef, MountedComponent.getComponentType());
        if (mounted != null) {
            Ref<EntityStore> mountedTo = mounted.getMountedToEntity();
            if (horseRef.equals(mountedTo)) return true;
            if (mountedTo != null && mountedTo.isValid()) {
                UUID mountedUuid = tryReadUuid(mountedTo);
                UUID horseUuid = tryReadUuid(horseRef);
                if (mountedUuid != null && horseUuid != null && mountedUuid.equals(horseUuid)) {
                    return true;
                }
            }
        }

        // via MountedByComponent no cavalo
        MountedByComponent mountedBy = store.getComponent(horseRef, MountedByComponent.getComponentType());
        if (mountedBy != null) {
            List<Ref<EntityStore>> passengers = mountedBy.getPassengers();
            if (passengers != null) {
                for (Ref<EntityStore> passenger : passengers) {
                    if (playerRef.equals(passenger)) return true;
                    if (playerObj != null && passenger != null) {
                        PlayerRef passengerObj = store.getComponent(passenger, PlayerRef.getComponentType());
                        if (passengerObj != null && passengerObj.getUuid().equals(playerObj.getUuid())) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
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
