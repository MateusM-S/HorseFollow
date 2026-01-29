package com.horsefollow.commands;

import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.horsefollow.FollowService;
import com.horsefollow.Localization;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class HorseFollowCommand extends AbstractCommand {

    private final FollowService service;

    // /horsefollow [action]
    // action: bind | unbind | status | distance | call | stay | reload | help
    private final OptionalArg<String> actionArg;

    public HorseFollowCommand(FollowService service) {
        super("horsefollow", "Bind a horse to follow you", false);

        this.service = service;
        setAllowsExtraArguments(true);

        // Argumento posicional opcional
        actionArg = withOptionalArg("action", "bind | unbind | status | distance | call | stay | reload | help", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        final Ref<EntityStore> playerRef;
        try {
            playerRef = context.senderAsPlayerRef();
        } catch (Throwable t) {
            context.sender().sendMessage(Message.raw(Localization.get(null, null, "horsefollow.command.only_players")));
            return CompletableFuture.completedFuture(null);
        }

        String input = context.getInputString();
        String[] parts = null;
        int actionIndex = -1;
        if (input != null) {
            parts = input.trim().split("\\s+");
            int idx = 0;
            if (parts.length > 0) {
                String first = parts[0];
                if (first.startsWith("/")) {
                    first = first.substring(1);
                }
                if (first.equalsIgnoreCase(context.getCalledCommand().getName())) {
                    idx = 1;
                }
            }
            if (parts.length > idx) {
                actionIndex = idx;
            }
        }

        String action = actionArg.provided(context) ? actionArg.get(context) : null;
        if (action == null || action.isBlank()) {
            if (parts != null && actionIndex >= 0 && parts.length > actionIndex) {
                action = parts[actionIndex];
            }
        }
        // Sem ação: não executa nada (cai no "usage")
        action = (action == null || action.isBlank()) ? "" : action.trim().toLowerCase(Locale.ROOT);
        final String actionFinal = action;
        final Double distanceValue = parseDistanceToken(parts, actionIndex);

        // Tudo que acessa ECS/componentes deve ir para a WorldThread
        Store<EntityStore> store = playerRef.getStore();
        Object entityStore = store.getExternalData();

        worldExecute(entityStore, () -> {
            try {
                switch (actionFinal) {
                    case "status": {
                        boolean bound = service.isBound(playerRef);
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, bound
                                ? "horsefollow.command.status.has_bond"
                                : "horsefollow.command.status.no_bond")));
                        break;
                    }
                    case "unbind":
                    case "desvincular": {
                        service.unbind(playerRef);
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.unbind.ok")));
                        break;
                    }
                    case "bind":
                    case "vincular": {
                        // Bind somente com o item (Horse_Feed/Ram_Feed + F). Comando /horsefollow bind: só operador e nunca montado.
                        MountedComponent mounted = store.getComponent(playerRef, MountedComponent.getComponentType());
                        if (mounted != null) {
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.bind.use_item_mounted")));
                            break;
                        }
                        if (!isOperator(context)) {
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.bind.operator_only")));
                            break;
                        }
                        Ref<EntityStore> fallbackHorse = findMountFromPassengers(store, playerRef);
                        if (fallbackHorse == null) {
                            fallbackHorse = findNpcMountByOwner(store, playerRef);
                        }
                        if (fallbackHorse != null && fallbackHorse.isValid()) {
                            service.bind(playerRef, fallbackHorse);
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.bind.ok")));
                        } else {
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.bind.use_item")));
                        }
                        break;
                    }
                    case "distance":
                    case "distancia": {
                        if (distanceValue == null || distanceValue < 0) {
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.distance.invalid")));
                            break;
                        }
                        boolean ok = service.updateTeleportDistance(distanceValue);
                        if (ok) {
                            if (distanceValue == 0) {
                                context.sender().sendMessage(Message.raw(Localization.get(
                                        store, playerRef, "horsefollow.command.distance.disabled")));
                            } else {
                                String template = Localization.get(store, playerRef, "horsefollow.command.distance.ok");
                                String message = String.format(Locale.ROOT, template, distanceValue);
                                context.sender().sendMessage(Message.raw(message));
                            }
                        } else {
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.distance.invalid")));
                        }
                        break;
                    }
                    case "call":
                    case "chamar": {
                        Ref<EntityStore> horseRef = service.getBoundHorse(playerRef);
                        if (horseRef == null || !horseRef.isValid()) {
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.call.no_bond")));
                            break;
                        }
                        boolean ok = service.teleportHorseNearPlayer(store, playerRef, horseRef);
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef,
                                ok ? "horsefollow.command.call.ok" : "horsefollow.command.call.fail")));
                        break;
                    }
                    case "stay":
                    case "ficar": {
                        if (!service.isBound(playerRef)) {
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.stay.no_bond")));
                            break;
                        }
                        // requestStay aplica imediatamente se desmontado; se montado, entra em fila (pendingStay)
                        FollowService.StayRequestResult res = service.requestStay(playerRef);
                        if (res == FollowService.StayRequestResult.APPLIED) {
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.stay.ok")));
                        } else if (res == FollowService.StayRequestResult.QUEUED) {
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.stay.queued")));
                        } else {
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.stay.fail")));
                        }
                        break;
                    }
                    case "reload":
                    case "resetar": {
                        service.reloadConfig();
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.reload.ok")));
                        break;
                    }
                    case "feedconsumed": {
                        if (!isOperator(context)) {
                            context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.bind.operator_only")));
                            break;
                        }
                        String message = Localization.get(store, playerRef, "horsefollow.feed.consumed");
                        context.sender().sendMessage(Message.raw(message));
                        break;
                    }
                    case "help": {
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.help.title")));
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.help.bind")));
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.help.unbind")));
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.help.status")));
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.help.distance")));
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.help.call")));
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.help.stay")));
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.help.reload")));
                        break;
                    }
                    default:
                        context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.usage")));
                        break;
                }
            } catch (Throwable t) {
                // fallback pra não derrubar o servidor
                try {
                    System.out.println("[HorseFollow] Command error action=" + actionFinal + " input=" + context.getInputString());
                    t.printStackTrace();
                } catch (Throwable ignored) {}
                try {
                    context.sender().sendMessage(Message.raw(Localization.get(store, playerRef, "horsefollow.command.error")));
                } catch (Throwable ignored) {}
            }
        });

        return CompletableFuture.completedFuture(null);
    }

    private static Double parseDistanceToken(String[] parts, int actionIndex) {
        if (parts == null || actionIndex < 0) return null;
        int valueIndex = actionIndex + 1;
        if (parts.length <= valueIndex) return null;
        String raw = parts[valueIndex];
        if (raw == null || raw.isBlank()) return null;
        try {
            String normalized = raw.trim().replace(',', '.');
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void worldExecute(Object entityStore, Runnable r) {
        try {
            Object world = null;

            try {
                Method m = entityStore.getClass().getMethod("getWorld");
                world = m.invoke(entityStore);
            } catch (NoSuchMethodException ignored) {}

            if (world == null) {
                try {
                    Method m = entityStore.getClass().getMethod("world");
                    world = m.invoke(entityStore);
                } catch (NoSuchMethodException ignored) {}
            }

            if (world == null) {
                r.run();
                return;
            }

            Method exec = world.getClass().getMethod("execute", Runnable.class);
            exec.invoke(world, r);
        } catch (Throwable t) {
            r.run();
        }
    }

    /** Verifica se o sender do comando é operador (hasPermission). */
    private static boolean isOperator(@Nonnull CommandContext context) {
        try {
            return context.sender().hasPermission("hytale.operator");
        } catch (Throwable ignored) {}
        return false;
    }

    private static Ref<EntityStore> findMountFromPassengers(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null) return null;
        com.hypixel.hytale.server.core.universe.PlayerRef playerObj =
                store.getComponent(playerRef, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
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
                        return;
                    }
                    if (playerObj != null) {
                        com.hypixel.hytale.server.core.universe.PlayerRef passengerObj =
                                store.getComponent(passenger, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
                        if (passengerObj != null && passengerObj.getUuid().equals(playerObj.getUuid())) {
                            found.set(chunk.getReferenceTo(i));
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
        com.hypixel.hytale.server.core.universe.PlayerRef playerObj =
                store.getComponent(playerRef, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
        if (playerObj == null) return null;
        AtomicReference<Ref<EntityStore>> found = new AtomicReference<>();
        Archetype<EntityStore> query = Archetype.of(NPCMountComponent.getComponentType());
        store.forEachChunk(query, (chunk, cb) -> {
            if (found.get() != null) return;
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                NPCMountComponent npcMount = chunk.getComponent(i, NPCMountComponent.getComponentType());
                if (npcMount == null) continue;
                com.hypixel.hytale.server.core.universe.PlayerRef owner = npcMount.getOwnerPlayerRef();
                if (owner != null && owner.getUuid().equals(playerObj.getUuid())) {
                    found.set(chunk.getReferenceTo(i));
                    return;
                }
            }
        });
        return found.get();
    }

}
