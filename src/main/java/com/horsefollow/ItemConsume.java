package com.horsefollow;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.Inventory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia envio de mensagem no chat quando Horse_Feed ou Ram_Feed é consumido.
 * Usa sendMessage() diretamente do PlayerRef (mesma lógica do comando /say).
 */
public final class ItemConsume {

    // playerRef -> última vez que enviamos mensagem (para evitar re-enviar)
    private final Map<Ref<EntityStore>, Long> lastScheduled = new ConcurrentHashMap<>();
    
    // playerRef -> última quantidade de Horse_Feed detectada (para detectar diminuição)
    private final Map<Ref<EntityStore>, Integer> lastHorseFeedQuantity = new ConcurrentHashMap<>();
    
    // playerRef -> última quantidade de Ram_Feed detectada (para detectar diminuição)
    private final Map<Ref<EntityStore>, Integer> lastRamFeedQuantity = new ConcurrentHashMap<>();
    
    // playerRef -> se já logamos que conseguimos acessar o Inventory (para não poluir o log)
    private final Map<Ref<EntityStore>, Boolean> inventoryAccessLogged = new ConcurrentHashMap<>();
    
    private long tickCounter = 0L;
    private static final long CHECK_INTERVAL_TICKS = 2; // Verifica a cada 2 ticks (200ms) para detectar rápido
    private static final String HORSE_FEED_ITEM_ID = "Horse_Feed";
    private static final String RAM_FEED_ITEM_ID = "Ram_Feed";

    public ItemConsume() {
    }

    /**
     * Tick periódico para verificar se players estão consumindo Horse_Feed ou Ram_Feed.
     * Recebe uma coleção de refs de players para verificar (ex: todos os online).
     * Deve ser chamado do mesmo Timer que chama FollowService.tick().
     */
    public void tick(Collection<Ref<EntityStore>> playersToCheck) {
        tickCounter++;
        
        if (playersToCheck == null || playersToCheck.isEmpty()) return;
        
        // Verifica a cada CHECK_INTERVAL_TICKS para não sobrecarregar
        if (tickCounter % CHECK_INTERVAL_TICKS != 0) return;
        
        for (Ref<EntityStore> playerRef : playersToCheck) {
            if (playerRef == null || !playerRef.isValid()) {
                lastScheduled.remove(playerRef);
                lastHorseFeedQuantity.remove(playerRef);
                lastRamFeedQuantity.remove(playerRef);
                inventoryAccessLogged.remove(playerRef);
                continue;
            }
            
            Store<EntityStore> store = playerRef.getStore();
            EntityStore entityStore = store != null ? store.getExternalData() : null;
            if (store != null && entityStore != null) {
                // Executa na world thread para acessar PlayerRef com segurança
                worldExecute(entityStore, () -> checkPlayerConsuming(store, playerRef));
            }
        }
    }
    
    /**
     * Helper para executar código na world thread (mesmo padrão do FollowService).
     */
    private static void worldExecute(Object entityStore, Runnable r) {
        try {
            Object world = null;
            
            // tenta getWorld()
            try {
                java.lang.reflect.Method m = entityStore.getClass().getMethod("getWorld");
                world = m.invoke(entityStore);
            } catch (NoSuchMethodException ignored) {}
            
            // tenta world()
            if (world == null) {
                try {
                    java.lang.reflect.Method m = entityStore.getClass().getMethod("world");
                    world = m.invoke(entityStore);
                } catch (NoSuchMethodException ignored) {}
            }
            
            if (world == null) {
                // fallback
                r.run();
                return;
            }
            
            // tenta execute(Runnable)
            java.lang.reflect.Method exec = world.getClass().getMethod("execute", Runnable.class);
            exec.invoke(world, r);
            
        } catch (Throwable t) {
            // fallback
            r.run();
        }
    }

    /**
     * Verifica se o player está consumindo Horse_Feed ou Ram_Feed e envia mensagem se necessário.
     * Detecta quando a quantidade de cada item no inventário diminui.
     * 
     * Baseado na análise do InventoryManagerAPI, tenta acessar o Inventory via:
     * 1. Componente ECS (InventoryComponent)
     * 2. Método direto no PlayerRef (getInventory())
     * 3. Reflexão como fallback
     */
    private void checkPlayerConsuming(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null || !playerRef.isValid()) {
            lastHorseFeedQuantity.remove(playerRef);
            lastRamFeedQuantity.remove(playerRef);
            return;
        }

        try {
            PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (player == null) {
                lastHorseFeedQuantity.remove(playerRef);
                lastRamFeedQuantity.remove(playerRef);
                return;
            }

            // Tenta obter o Inventory do player
            Inventory inventory = getPlayerInventory(store, playerRef, player);
            if (inventory == null) {
                return;
            }

            // Horse_Feed
            Integer currentHorse = getItemQuantity(inventory, HORSE_FEED_ITEM_ID);
            if (currentHorse != null) {
                Integer lastHorse = lastHorseFeedQuantity.get(playerRef);
                lastHorseFeedQuantity.put(playerRef, currentHorse);
                if (lastHorse != null && currentHorse < lastHorse) {
                    System.out.println("[HorseFollow] ItemConsume: Detectada diminuição de Horse_Feed: " + lastHorse + " -> " + currentHorse);
                    tryNotifyConsumed(store, playerRef, true);
                }
            } else {
                lastHorseFeedQuantity.remove(playerRef);
            }

            // Ram_Feed (mesma lógica)
            Integer currentRam = getItemQuantity(inventory, RAM_FEED_ITEM_ID);
            if (currentRam != null) {
                Integer lastRam = lastRamFeedQuantity.get(playerRef);
                lastRamFeedQuantity.put(playerRef, currentRam);
                if (lastRam != null && currentRam < lastRam) {
                    System.out.println("[HorseFollow] ItemConsume: Detectada diminuição de Ram_Feed: " + lastRam + " -> " + currentRam);
                    tryNotifyConsumed(store, playerRef, false);
                }
            } else {
                lastRamFeedQuantity.remove(playerRef);
            }
            
        } catch (Throwable t) {
            System.out.println("[HorseFollow] ItemConsume: Erro ao verificar consumo: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
    }

    /**
     * Envia mensagem de consumo (Horse_Feed ou Ram_Feed), com throttle de 2s por player.
     */
    private void tryNotifyConsumed(Store<EntityStore> store, Ref<EntityStore> playerRef, boolean horseFeed) {
        long now = System.currentTimeMillis();
        Long lastScheduledTime = lastScheduled.get(playerRef);
        if (lastScheduledTime != null && (now - lastScheduledTime) <= 2000) {
            System.out.println("[HorseFollow] ItemConsume: Mensagem já enviada recentemente, ignorando");
            return;
        }
        if (horseFeed) {
            notifyConsumed(store, playerRef);
        } else {
            notifyRamFeedConsumed(store, playerRef);
        }
        lastScheduled.put(playerRef, now);
    }
    
    /**
     * Tenta obter o Inventory do player usando múltiplas estratégias.
     * Baseado na análise do InventoryManagerAPI.
     * 
     * DESCOBERTA: O InventoryManagerAPI usa:
     * - com.hypixel.hytale.server.core.entity.entities.Player.getInventory()
     * - Não PlayerRef.getInventory()
     */
    private Inventory getPlayerInventory(Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef player) {
        // Estratégia 1: Tentar obter componente Player e chamar getInventory() (como o InventoryManagerAPI faz)
        try {
            // Tenta obter o componente Player
            Class<?> playerComponentClass = Class.forName("com.hypixel.hytale.server.core.entity.entities.Player");
            Method getComponentType = playerComponentClass.getMethod("getComponentType");
            Object playerComponentType = getComponentType.invoke(null);
            
            // Obtém o componente Player do store
            Method getComponent = store.getClass().getMethod("getComponent", Ref.class, playerComponentType.getClass());
            Object playerComponent = getComponent.invoke(store, playerRef, playerComponentType);
            
            if (playerComponent != null) {
                // Chama getInventory() no componente Player
                Method getInventory = playerComponentClass.getMethod("getInventory");
                Inventory inventory = (Inventory) getInventory.invoke(playerComponent);
                if (inventory != null) {
                    // Log apenas na primeira vez que conseguimos acessar
                    if (!inventoryAccessLogged.getOrDefault(playerRef, false)) {
                        System.out.println("[HorseFollow] ItemConsume: Inventory obtido via Player.getInventory() (como InventoryManagerAPI)");
                        inventoryAccessLogged.put(playerRef, true);
                    }
                    return inventory;
                }
            }
        } catch (ClassNotFoundException e) {
            System.out.println("[HorseFollow] ItemConsume: Classe Player não encontrada: " + e.getMessage());
        } catch (NoSuchMethodException e) {
            System.out.println("[HorseFollow] ItemConsume: Método não encontrado: " + e.getMessage());
        } catch (Throwable t) {
            System.out.println("[HorseFollow] ItemConsume: Erro ao obter Inventory via Player: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
        
        // Estratégia 2: Tentar método direto no PlayerRef (fallback)
        try {
            Method getInventory = player.getClass().getMethod("getInventory");
            Inventory inventory = (Inventory) getInventory.invoke(player);
            if (inventory != null) {
                // Log apenas na primeira vez que conseguimos acessar
                if (!inventoryAccessLogged.getOrDefault(playerRef, false)) {
                    System.out.println("[HorseFollow] ItemConsume: Inventory obtido via PlayerRef.getInventory()");
                    inventoryAccessLogged.put(playerRef, true);
                }
                return inventory;
            }
        } catch (NoSuchMethodException ignored) {
            // Método não existe
        } catch (Throwable t) {
            System.out.println("[HorseFollow] ItemConsume: Erro ao chamar PlayerRef.getInventory(): " + t.getMessage());
        }
        
        // Estratégia 3: Tentar outros nomes de método comuns
        String[] methodNames = {"getPlayerInventory", "inventory", "getItemInventory"};
        for (String methodName : methodNames) {
            try {
                Method method = player.getClass().getMethod(methodName);
                Object result = method.invoke(player);
                if (result instanceof Inventory) {
                    // Log apenas na primeira vez que conseguimos acessar
                    if (!inventoryAccessLogged.getOrDefault(playerRef, false)) {
                        System.out.println("[HorseFollow] ItemConsume: Inventory obtido via PlayerRef." + methodName + "()");
                        inventoryAccessLogged.put(playerRef, true);
                    }
                    return (Inventory) result;
                }
            } catch (NoSuchMethodException ignored) {
                // Método não existe
            } catch (Throwable ignored) {
                // Erro ao invocar
            }
        }
        
        return null;
    }
    
    /**
     * Obtém a quantidade de um item no inventário (ex.: Horse_Feed, Ram_Feed).
     * Tenta múltiplas estratégias para encontrar o item.
     */
    private Integer getItemQuantity(Inventory inventory, String itemId) {
        if (inventory == null || itemId == null) return null;
        
        try {
            // Estratégia 1: Tentar método getItem(String itemId)
            try {
                Method getItem = inventory.getClass().getMethod("getItem", String.class);
                Object itemStack = getItem.invoke(inventory, itemId);
                if (itemStack != null) {
                    Method getQuantity = itemStack.getClass().getMethod("getQuantity");
                    Integer quantity = (Integer) getQuantity.invoke(itemStack);
                    if (quantity != null && quantity > 0) {
                        return quantity;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                System.out.println("[HorseFollow] ItemConsume: Erro ao chamar getItem(): " + t.getMessage());
            }
            
            // Estratégia 2: Tentar método findItem(String itemId)
            try {
                Method findItem = inventory.getClass().getMethod("findItem", String.class);
                Object itemStack = findItem.invoke(inventory, itemId);
                if (itemStack != null) {
                    Method getQuantity = itemStack.getClass().getMethod("getQuantity");
                    Integer quantity = (Integer) getQuantity.invoke(itemStack);
                    if (quantity != null && quantity > 0) {
                        return quantity;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
            }
            
            // Estratégia 3: Tentar obter item segurado (held item)
            try {
                Method getHeldItem = inventory.getClass().getMethod("getHeldItem");
                Object heldItem = getHeldItem.invoke(inventory);
                if (heldItem != null) {
                    Method getItemIdMethod = heldItem.getClass().getMethod("getItemId");
                    String heldId = (String) getItemIdMethod.invoke(heldItem);
                    if (itemId.equals(heldId)) {
                        Method getQuantity = heldItem.getClass().getMethod("getQuantity");
                        Integer quantity = (Integer) getQuantity.invoke(heldItem);
                        if (quantity != null && quantity > 0) {
                            return quantity;
                        }
                    }
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
            }
            
            // Estratégia 4: Tentar outros nomes de método
            String[] heldItemMethods = {"getItemInHand", "itemInHand", "heldItem"};
            for (String methodName : heldItemMethods) {
                try {
                    Method method = inventory.getClass().getMethod(methodName);
                    Object heldItem = method.invoke(inventory);
                    if (heldItem != null) {
                        Method getItemIdMethod = heldItem.getClass().getMethod("getItemId");
                        String heldId = (String) getItemIdMethod.invoke(heldItem);
                        if (itemId.equals(heldId)) {
                            Method getQuantity = heldItem.getClass().getMethod("getQuantity");
                            Integer quantity = (Integer) getQuantity.invoke(heldItem);
                            if (quantity != null && quantity > 0) {
                                return quantity;
                            }
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                }
            }
            
        } catch (Throwable t) {
            System.out.println("[HorseFollow] ItemConsume: Erro ao obter quantidade: " + t.getMessage());
        }
        
        return null;
    }
    
    /**
     * Método público para notificar consumo de Horse_Feed manualmente.
     * Usa sendMessage() diretamente do PlayerRef (mesma lógica do comando /say).
     */
    public void notifyConsumed(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        sendConsumedMessage(store, playerRef, "horsefollow.feed.consumed");
    }

    /**
     * Notifica consumo de Ram_Feed (mesma lógica que Horse_Feed).
     */
    public void notifyRamFeedConsumed(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        sendConsumedMessage(store, playerRef, "horsefollow.ramfeed.consumed");
    }

    private void sendConsumedMessage(Store<EntityStore> store, Ref<EntityStore> playerRef, String localeKey) {
        if (store == null || playerRef == null || !playerRef.isValid()) return;
        
        try {
            PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (player != null) {
                String message = Localization.get(store, playerRef, localeKey);
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(Message.raw(message));
                }
            }
        } catch (Throwable t) {
            System.out.println("[HorseFollow] ItemConsume: Erro ao enviar mensagem: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
    }
    
    /**
     * Executa o comando /horsefollow feedconsumed programaticamente para um player.
     * Usa a mesma lógica do comando /say (sendMessage()).
     */
    public void executeFeedConsumedCommand(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        // Em vez de executar o comando via CommandRegistry (que é complexo),
        // vamos chamar notifyConsumed() diretamente, que usa sendMessage() (mesma lógica do /say)
        notifyConsumed(store, playerRef);
    }

    /**
     * Limpa o estado quando o player sai.
     */
    public void onPlayerDisconnect(Ref<EntityStore> playerRef) {
        if (playerRef != null) {
            lastScheduled.remove(playerRef);
            lastHorseFeedQuantity.remove(playerRef);
            lastRamFeedQuantity.remove(playerRef);
            inventoryAccessLogged.remove(playerRef);
        }
    }

    /**
     * Limpa todos os estados (útil para shutdown).
     */
    public void clearAll() {
        lastScheduled.clear();
        lastHorseFeedQuantity.clear();
        lastRamFeedQuantity.clear();
        inventoryAccessLogged.clear();
    }
}
