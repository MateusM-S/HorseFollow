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

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class HorseFollowCommand extends AbstractCommand {

    private final FollowService service;

    // /horsefollow [action]
    // action: bind | unbind | status
    private final OptionalArg<String> actionArg;

    public HorseFollowCommand(FollowService service) {
        super("horsefollow", "Bind a horse to follow you", false);

        this.service = service;
        setAllowsExtraArguments(true);

        // Argumento posicional opcional
        actionArg = withOptionalArg("action", "bind | unbind | status", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        final Ref<EntityStore> playerRef;
        try {
            playerRef = context.senderAsPlayerRef();
        } catch (Throwable t) {
            context.sender().sendMessage(Message.raw("[HorseFollow] O comando só funciona para jogadores."));
            return CompletableFuture.completedFuture(null);
        }

        String action = actionArg.provided(context) ? actionArg.get(context) : null;
        if (action == null || action.isBlank()) {
            String input = context.getInputString();
            if (input != null) {
                String[] parts = input.trim().split("\\s+");
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
                    action = parts[idx];
                }
            }
        }
        action = (action == null || action.isBlank()) ? "status" : action.trim().toLowerCase(Locale.ROOT);
        final String actionFinal = action;

        // Tudo que acessa ECS/componentes deve ir para a WorldThread
        Store<EntityStore> store = playerRef.getStore();
        Object entityStore = store.getExternalData();

        worldExecute(entityStore, () -> {
            try {
                switch (actionFinal) {
                    case "status": {
                        boolean bound = service.isBound(playerRef);
                        context.sender().sendMessage(Message.raw(bound
                                ? "[HorseFollow] status: Você possui 01 vínculo."
                                : "[HorseFollow] status: Você não possui vínculos ativos."));
                        break;
                    }
                    case "unbind": {
                        service.unbind(playerRef);
                        context.sender().sendMessage(Message.raw("[HorseFollow] O vínculo foi quebrado."));
                        break;
                    }
                    case "bind": {
                        MountedComponent mounted = store.getComponent(playerRef, MountedComponent.getComponentType());
                        if (mounted == null) {
                            Ref<EntityStore> fallbackHorse = findMountFromPassengers(store, playerRef);
                            if (fallbackHorse == null) {
                                fallbackHorse = findNpcMountByOwner(store, playerRef);
                            }
                            if (fallbackHorse != null && fallbackHorse.isValid()) {
                                service.bind(playerRef, fallbackHorse);
                                context.sender().sendMessage(Message.raw("[HorseFollow] O vínculo foi criado com sucesso!"));
                            } else {
                                service.unbind(playerRef);
                            context.sender().sendMessage(Message.raw("[HorseFollow] Monte no cavalo primeiro."));
                            }
                            break;
                        }

                        Ref<EntityStore> horseRef = mounted.getMountedToEntity();
                        if (horseRef == null || !horseRef.isValid()) {
                            service.unbind(playerRef);
                            context.sender().sendMessage(Message.raw("[HorseFollow] Montaria inválida."));
                            break;
                        }

                        Ref<EntityStore> resolvedHorse = resolveBoundHorse(store, playerRef, horseRef);
                        if (resolvedHorse != null && resolvedHorse.isValid()) {
                            horseRef = resolvedHorse;
                        }

                        service.bind(playerRef, horseRef);
                        context.sender().sendMessage(Message.raw("[HorseFollow] O vínculo foi criado com sucesso!"));
                        break;
                    }
                    default:
                        context.sender().sendMessage(Message.raw("[HorseFollow] uso: /horsefollow [bind|unbind|status]"));
                        break;
                }
            } catch (Throwable t) {
                // fallback pra não derrubar o servidor
                try {
                    context.sender().sendMessage(Message.raw("[HorseFollow] erro ao executar (ver server.log)."));
                } catch (Throwable ignored) {}
            }
        });

        return CompletableFuture.completedFuture(null);
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

    private static Ref<EntityStore> resolveBoundHorse(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> mountedRef
    ) {
        if (store == null || playerRef == null || mountedRef == null) return null;

        MountedByComponent mountedBy = store.getComponent(mountedRef, MountedByComponent.getComponentType());
        if (mountedBy != null) {
            List<Ref<EntityStore>> passengers = mountedBy.getPassengers();
            if (passengers != null) {
                for (Ref<EntityStore> passenger : passengers) {
                    if (playerRef.equals(passenger)) {
                        return mountedRef;
                    }
                }
            }
        }

        MountedComponent mounted = store.getComponent(mountedRef, MountedComponent.getComponentType());
        if (mounted != null) {
            Ref<EntityStore> mountedTo = mounted.getMountedToEntity();
            if (mountedTo != null && mountedTo.isValid()) {
                return mountedTo;
            }
        }

        Ref<EntityStore> fallback = findMountFromPassengers(store, playerRef);
        if (fallback != null) return fallback;
        return findNpcMountByOwner(store, playerRef);
    }
}
