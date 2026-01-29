package com.horsefollow;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ao consumir Horse_Feed ou Ram_Feed: verifica target (Horse/Ram no alcance).
 * Se target válido: faz bind (vínculo) e mensagem de sucesso.
 * Se sem target: devolve 1 item (refund) e mensagem de erro.
 */
public final class ItemConsume {

    private final FollowService followService;

    // playerRef -> última vez que processamos feed (throttle)
    private final Map<Ref<EntityStore>, Long> lastScheduled = new ConcurrentHashMap<>();
    // playerRef -> última vez que tryFeedFromFKey fez bind com sucesso (evita tick enviar "no_target" logo depois)
    private final Map<Ref<EntityStore>, Long> lastFeedHandledByFKey = new ConcurrentHashMap<>();
    // playerRef -> última quantidade de Horse_Feed detectada (para detectar diminuição)
    private final Map<Ref<EntityStore>, Integer> lastHorseFeedQuantity = new ConcurrentHashMap<>();
    // playerRef -> última quantidade de Ram_Feed detectada (para detectar diminuição)
    private final Map<Ref<EntityStore>, Integer> lastRamFeedQuantity = new ConcurrentHashMap<>();

    /** Janela em ms: se tryFeedFromFKey fez bind há menos que isso, tryNotifyConsumed (tick) não envia "no_target". */
    private static final long FEED_FKEY_COOLDOWN_MS = 2500L;

    private long tickCounter = 0L;
    private static final long CHECK_INTERVAL_TICKS = 2; // Verifica a cada 2 ticks (200ms)
    private static final String HORSE_FEED_ITEM_ID = "Horse_Feed";
    private static final String RAM_FEED_ITEM_ID = "Ram_Feed";

    public ItemConsume(FollowService followService) {
        this.followService = followService;
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
                lastFeedHandledByFKey.remove(playerRef);
                lastHorseFeedQuantity.remove(playerRef);
                lastRamFeedQuantity.remove(playerRef);
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
     * Executa código na world thread (mesmo padrão do FollowService).
     * Público para uso pelo FeedOnFKeyFilter.
     */
    public static void runOnWorldThread(Object entityStore, Runnable r) {
        worldExecute(entityStore, r);
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
     * Verifica se o player consumiu Horse_Feed ou Ram_Feed (diminuição de quantidade).
     * Se target válido no alcance: faz bind e mensagem de sucesso.
     * Se sem target: devolve 1 item (refund) e mensagem de erro.
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

            Inventory inventory = getPlayerInventory(store, playerRef, player);
            if (inventory == null) return;

            // Horse_Feed: detecta diminuição de quantidade (engine consumiu no charge)
            Integer currentHorse = getItemQuantity(inventory, HORSE_FEED_ITEM_ID);
            if (currentHorse != null) {
                Integer lastHorse = lastHorseFeedQuantity.get(playerRef);
                lastHorseFeedQuantity.put(playerRef, currentHorse);
                if (lastHorse != null && currentHorse < lastHorse) {
                    tryNotifyConsumed(store, playerRef, true);
                }
            } else {
                lastHorseFeedQuantity.remove(playerRef);
            }

            // Ram_Feed: mesma lógica
            Integer currentRam = getItemQuantity(inventory, RAM_FEED_ITEM_ID);
            if (currentRam != null) {
                Integer lastRam = lastRamFeedQuantity.get(playerRef);
                lastRamFeedQuantity.put(playerRef, currentRam);
                if (lastRam != null && currentRam < lastRam) {
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
     * Ao detectar consumo de Feed (diminuição de quantidade): verifica target no alcance.
     * Se válido: bind e mensagem. Se sem target: refund + mensagem.
     * Ignora se o consumo foi há pouco pela tecla F (tryFeedFromFKey), para não enviar "no_target" após bind ok.
     */
    private void tryNotifyConsumed(Store<EntityStore> store, Ref<EntityStore> playerRef, boolean horseFeed) {
        long now = System.currentTimeMillis();
        Long lastFKey = lastFeedHandledByFKey.get(playerRef);
        if (lastFKey != null && (now - lastFKey) <= FEED_FKEY_COOLDOWN_MS) {
            return; // Consumo já tratado pela tecla F; não enviar "no_target" nem refund
        }
        Long lastSched = lastScheduled.get(playerRef);
        if (lastSched != null && (now - lastSched) <= 2000) return;
        lastScheduled.put(playerRef, now);

        double range = followService.getFeedBindRange();
        Ref<EntityStore> target = followService.findTargetMount(store, playerRef, range, horseFeed);
        String itemId = horseFeed ? HORSE_FEED_ITEM_ID : RAM_FEED_ITEM_ID;

        if (target == null || !target.isValid()) {
            refundHeldItem(store, playerRef, itemId);
            sendConsumedMessage(store, playerRef, "horsefollow.feed.no_target");
            return;
        }
        followService.bind(playerRef, target);
        sendConsumedMessage(store, playerRef, "horsefollow.command.bind.ok");
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
                Method getInventory = playerComponentClass.getMethod("getInventory");
                Inventory inventory = (Inventory) getInventory.invoke(playerComponent);
                if (inventory != null) return inventory;
            }
        } catch (Throwable ignored) {
        }
        
        // Estratégia 2: método direto no PlayerRef
        try {
            Method getInventory = player.getClass().getMethod("getInventory");
            Inventory inventory = (Inventory) getInventory.invoke(player);
            if (inventory != null) return inventory;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) {
        }
        
        // Estratégia 3: outros nomes de método comuns
        for (String methodName : new String[] { "getPlayerInventory", "inventory", "getItemInventory" }) {
            try {
                Method method = player.getClass().getMethod(methodName);
                Object result = method.invoke(player);
                if (result instanceof Inventory) return (Inventory) result;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
    
    /**
     * Devolve 1 unidade do item ao jogador (held item ou stack do mesmo itemId).
     * Usado quando o feed é consumido pelo engine mas não havia target válido.
     */
    private void refundHeldItem(Store<EntityStore> store, Ref<EntityStore> playerRef, String itemId) {
        if (store == null || playerRef == null || itemId == null) return;
        try {
            PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (player == null) return;
            Inventory inventory = getPlayerInventory(store, playerRef, player);
            if (inventory == null) return;
            // Tenta addItem(itemId, 1)
            try {
                Method addItem = inventory.getClass().getMethod("addItem", String.class, int.class);
                addItem.invoke(inventory, itemId, 1);
                return;
            } catch (NoSuchMethodException ignored) {}
            try {
                Method addItem = inventory.getClass().getMethod("addItem", String.class, Integer.TYPE);
                addItem.invoke(inventory, itemId, 1);
                return;
            } catch (NoSuchMethodException ignored) {}
            // Fallback: tentar getHeldItem e setQuantity(getQuantity()+1)
            try {
                Method getHeld = inventory.getClass().getMethod("getHeldItem");
                Object held = getHeld.invoke(inventory);
                if (held != null) {
                    Method getItemIdM = held.getClass().getMethod("getItemId");
                    String heldId = (String) getItemIdM.invoke(held);
                    if (itemId.equals(heldId)) {
                        Method getQ = held.getClass().getMethod("getQuantity");
                        Integer q = (Integer) getQ.invoke(held);
                        if (q != null) {
                            Method setQ = held.getClass().getMethod("setQuantity", int.class);
                            setQ.invoke(held, q + 1);
                            return;
                        }
                    }
                }
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {
        }
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
            } catch (Throwable ignored) {
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
            
        } catch (Throwable ignored) {
        }
        return null;
    }
    
    /**
     * Feed pela tecla F: target no alcance → consome 1, bind e mensagem; sem target → só mensagem (não altera inventário).
     * Deve ser chamado na world thread (ex.: a partir de FeedOnFKeyFilter).
     */
    public void tryFeedFromFKey(Store<EntityStore> store, Ref<EntityStore> playerRef, boolean horseFeed) {
        if (store == null || playerRef == null || !playerRef.isValid()) return;
        double range = followService.getFeedBindRange();
        Ref<EntityStore> target = followService.findTargetMount(store, playerRef, range, horseFeed);
        String itemId = horseFeed ? HORSE_FEED_ITEM_ID : RAM_FEED_ITEM_ID;
        if (target == null || !target.isValid()) {
            sendConsumedMessage(store, playerRef, "horsefollow.feed.no_target");
            return;
        }
        if (!consumeOneHeldItem(store, playerRef, itemId)) return;
        followService.bind(playerRef, target);
        lastFeedHandledByFKey.put(playerRef, System.currentTimeMillis());
        playFeedConsumeSound(store, playerRef);
        sendConsumedMessage(store, playerRef, "horsefollow.command.bind.ok");
    }

    /**
     * Remove 1 unidade do item (itemId) do inventário do jogador (item na mão / hotbar ativa).
     * Usa a API oficial: getItemInHand(), getHotbar(), getActiveHotbarSlot(), setItemStackForSlot/removeItemStackFromSlot.
     * Retorna true se removeu, false se não conseguiu (ex.: item não encontrado ou quantidade 0).
     */
    public boolean consumeOneHeldItem(Store<EntityStore> store, Ref<EntityStore> playerRef, String itemId) {
        if (store == null || playerRef == null || itemId == null) return false;
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) return false;
        Inventory inventory = getPlayerInventory(store, playerRef, player);
        if (inventory == null) return false;
        try {
            // API oficial: getItemInHand() e hotbar
            ItemStack inHand = inventory.getItemInHand();
            if (inHand == null || inHand.isEmpty() || !itemId.equals(inHand.getItemId())) {
                return false;
            }
            int q = inHand.getQuantity();
            if (q < 1) return false;
            ItemContainer hotbar = inventory.getHotbar();
            if (hotbar == null) return false;
            short slot = (short) (inventory.getActiveHotbarSlot() & 0xFF);
            if (q == 1) {
                hotbar.setItemStackForSlot(slot, ItemStack.EMPTY);
            } else {
                hotbar.setItemStackForSlot(slot, inHand.withQuantity(q - 1));
            }
            inventory.markChanged();
            return true;
        } catch (Throwable t) {
            System.out.println("[HorseFollow] ItemConsume: Erro ao consumir 1 item (F): " + t.getMessage());
            return false;
        }
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

    /** Volume do som de feed (1.0 = padrão). Aumentado para ficar mais audível. */
    private static final float FEED_SOUND_VOLUME = 1.5f;

    /** IDs do SoundEvent de consumo (feed). Primeiro é o do mod; demais são fallbacks. */
    private static final String[] FEED_CONSUME_SOUND_IDS = {
        "Consume_Bread_Sound",
        "SFX/Items/Consume_Bread_Stereo_01",
        "SFX_Items_Consume_Bread_Stereo_01"
    };

    /** Log de falha do som apenas uma vez por sessão para não poluir o log. */
    private static volatile boolean soundFailureLogged = false;

    /**
     * Toca o som de consumo (pão/feed) na posição do jogador, após feed com sucesso.
     * Usa SoundUtil.playSoundEvent3dToPlayer (API Hytale). Índice obtido por reflexão.
     */
    private void playFeedConsumeSound(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null || !playerRef.isValid()) return;
        try {
            Integer index = getSoundEventIndex();
            if (index == null || index < 0) {
                if (!soundFailureLogged) {
                    soundFailureLogged = true;
                    System.out.println("[HorseFollow] ItemConsume: Som de feed não tocado — não foi possível obter índice do SoundEvent (testados: " + String.join(", ", FEED_CONSUME_SOUND_IDS) + "). Ver getSoundEventIndex no log acima.");
                }
                return;
            }
            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform == null) {
                if (!soundFailureLogged) {
                    soundFailureLogged = true;
                    System.out.println("[HorseFollow] ItemConsume: Som de feed não tocado — TransformComponent do jogador é null.");
                }
                return;
            }
            var pos = transform.getPosition();
            SoundUtil.playSoundEvent3dToPlayer(playerRef, index.intValue(), SoundCategory.SFX, pos.getX(), pos.getY(), pos.getZ(), FEED_SOUND_VOLUME, 1.0f, store);
        } catch (Throwable t) {
            if (!soundFailureLogged) {
                soundFailureLogged = true;
                System.out.println("[HorseFollow] ItemConsume: Erro ao tocar som de feed: " + t.getClass().getSimpleName() + " — " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    /**
     * Obtém o índice do SoundEvent para o som de feed (AssetRegistry + store de SoundEvent).
     */
    private static Integer getSoundEventIndex() {
        for (String soundEventId : FEED_CONSUME_SOUND_IDS) {
            Integer idx = tryGetSoundEventIndex(soundEventId);
            if (idx != null && idx >= 0) return idx;
        }
        return null;
    }

    /**
     * Obtém o índice do SoundEvent por ID via AssetRegistry + store de SoundEvent (config).
     * O protocolo SoundEvent não tem getAssetMap(); o índice vem do AssetStore do servidor (IndexedLookupTableAssetMap).
     */
    private static Integer tryGetSoundEventIndex(String soundEventId) {
        // AssetRegistry.getAssetStore(ConfigSoundEvent.class) -> store.getAssetMap() -> map.getIndex(id)
        try {
            Class<?> configClass = Class.forName("com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent");
            Class<?> registryClass = Class.forName("com.hypixel.hytale.assetstore.AssetRegistry");
            java.lang.reflect.Method getAssetStore = registryClass.getMethod("getAssetStore", Class.class);
            Object store = getAssetStore.invoke(null, configClass);
            if (store == null) return null;
            java.lang.reflect.Method getAssetMap = store.getClass().getMethod("getAssetMap");
            Object map = getAssetMap.invoke(store);
            if (map == null) return null;
            java.lang.reflect.Method getIndex = map.getClass().getMethod("getIndex", Object.class);
            Object idx = getIndex.invoke(map, soundEventId);
            if (idx instanceof Number) {
                int i = ((Number) idx).intValue();
                // IndexedAssetMap usa NOT_FOUND (geralmente -1) quando não encontra
                if (i >= 0) return i;
            }
        } catch (Throwable t) {
            if (!soundFailureLogged) {
                soundFailureLogged = true;
                System.out.println("[HorseFollow] ItemConsume: getSoundEventIndex falhou para '" + soundEventId + "': " + t.getMessage());
            }
        }
        return null;
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
     * Limpa o estado quando o player sai.
     */
    public void onPlayerDisconnect(Ref<EntityStore> playerRef) {
        if (playerRef != null) {
            lastScheduled.remove(playerRef);
            lastFeedHandledByFKey.remove(playerRef);
            lastHorseFeedQuantity.remove(playerRef);
            lastRamFeedQuantity.remove(playerRef);
        }
    }

    /**
     * Limpa todos os estados (útil para shutdown).
     */
    public void clearAll() {
        lastScheduled.clear();
        lastFeedHandledByFKey.clear();
        lastHorseFeedQuantity.clear();
        lastRamFeedQuantity.clear();
    }
}
