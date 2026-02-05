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

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;

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

    /** Cooldown do Chifre (Horn): 1 s após o uso completar antes de poder usar de novo. */
    private static final long HORN_USE_COOLDOWN_MS = 1000L;
    private final Map<Ref<EntityStore>, Long> lastHornUseCompleteTime = new ConcurrentHashMap<>();
    /** Início da última carga do Horn (evita spam: cliente envia muitos Secondary enquanto segura). */
    private final Map<Ref<EntityStore>, Long> lastHornChargeStartTime = new ConcurrentHashMap<>();
    /** Janela em ms: não iniciar nova carga se a última começou há menos que isso (5,5s animação + margem). */
    private static final long HORN_CHARGE_START_THROTTLE_MS = 6000L;

    private long tickCounter = 0L;
    private static final long CHECK_INTERVAL_TICKS = 2; // Verifica a cada 2 ticks (200ms)
    private static final String HORSE_FEED_ITEM_ID = "Horse_Feed";
    private static final String RAM_FEED_ITEM_ID = "Ram_Feed";

    /** Delay do Chifre: 4,75s (0,75s levar à boca + 4s tocando) antes de executar o call. */
    public static final long HORN_CHARGE_MS = 4750L;
    /** Atraso em ms para tocar o som do chifre (quando o jogador leva à boca: 0,75s). */
    private static final long HORN_SOUND_DELAY_MS = 750L;

    private final HorseFollowPlugin plugin;

    public ItemConsume(FollowService followService, HorseFollowPlugin plugin) {
        this.followService = followService;
        this.plugin = plugin;
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
        } catch (Throwable t) { }
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
            return false;
        }
    }

    private static final String SOUL_AMULET_ITEM_ID = "Soul_Amulet";
    private static final String SOUL_AMULET_FULL_ITEM_ID = "Soul_Amulet_Full";
    private static final String METADATA_SOUL_UUID = "soulUuid";
    private static final String METADATA_ROLE_INDEX = "roleIndex";

    /**
     * Substitui Soul_Amulet (vazio) na mão por Soul_Amulet_Full com alma no metadata do item (1 cavalo por amuleto).
     */
    public boolean replaceHeldWithFilledSoulAmulet(Store<EntityStore> store, Ref<EntityStore> playerRef,
                                                   java.util.UUID npcUuid, int roleIndex) {
        if (store == null || playerRef == null) return false;
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) return false;
        Inventory inventory = getPlayerInventory(store, playerRef, player);
        if (inventory == null) return false;
        try {
            ItemStack inHand = inventory.getItemInHand();
            if (inHand == null || inHand.isEmpty() || !SOUL_AMULET_ITEM_ID.equals(inHand.getItemId())) {
                return false;
            }
            BsonDocument meta = new BsonDocument()
                    .append(METADATA_SOUL_UUID, new BsonString(npcUuid.toString()))
                    .append(METADATA_ROLE_INDEX, new BsonInt32(roleIndex));
            ItemStack filled = new ItemStack(SOUL_AMULET_FULL_ITEM_ID, 1, meta);
            ItemContainer hotbar = inventory.getHotbar();
            if (hotbar == null) return false;
            short slot = (short) (inventory.getActiveHotbarSlot() & 0xFF);
            hotbar.setItemStackForSlot(slot, filled);
            inventory.markChanged();
            SoulAmuletPersistence.save(player.getUuid(), npcUuid, roleIndex);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Retorna true se o jogador está com Soul_Amulet ou Soul_Amulet_Full na mão.
     */
    public static boolean isHoldingSoulAmulet(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null) return false;
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) return false;
        Inventory inventory = getPlayerInventoryStatic(store, playerRef, player);
        if (inventory == null) return false;
        try {
            ItemStack inHand = inventory.getItemInHand();
            if (inHand == null || inHand.isEmpty()) return false;
            String id = inHand.getItemId();
            return SOUL_AMULET_ITEM_ID.equals(id) || SOUL_AMULET_FULL_ITEM_ID.equals(id);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Obtém UUID e roleIndex do amuleto cheio na mão. Prioriza metadata do item (1 alma por amuleto); fallback souls.txt.
     */
    public static SoulAmuletData getHeldSoulAmuletData(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null) return null;
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) return null;
        Inventory inventory = getPlayerInventoryStatic(store, playerRef, player);
        if (inventory == null) return null;
        try {
            ItemStack inHand = inventory.getItemInHand();
            if (inHand == null || inHand.isEmpty()) return null;
            if (!SOUL_AMULET_FULL_ITEM_ID.equals(inHand.getItemId())) return null;
            BsonDocument meta = inHand.getMetadata();
            if (meta != null && meta.containsKey(METADATA_SOUL_UUID) && meta.containsKey(METADATA_ROLE_INDEX)) {
                try {
                    String uuidStr = meta.getString(METADATA_SOUL_UUID).getValue();
                    int role = meta.getInt32(METADATA_ROLE_INDEX).getValue();
                    return new SoulAmuletData(java.util.UUID.fromString(uuidStr), role);
                } catch (Exception ignored) {}
            }
            return SoulAmuletPersistence.load(player.getUuid());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Retorna true se o jogador tem algum Soul_Amulet_Full (em qualquer slot) com esta alma.
     */
    public static boolean playerHasSoulInAnyAmulet(Store<EntityStore> store, Ref<EntityStore> playerRef, java.util.UUID soulUuid) {
        if (store == null || playerRef == null || soulUuid == null) return false;
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) return false;
        Inventory inventory = getPlayerInventoryStatic(store, playerRef, player);
        if (inventory == null) return false;
        try {
            ItemContainer hotbar = inventory.getHotbar();
            if (hotbar == null) return false;
            int slots = hotbar.getCapacity();
            for (int i = 0; i < slots; i++) {
                ItemStack stack = hotbar.getItemStack((short) i);
                if (stack == null || stack.isEmpty()) continue;
                if (!SOUL_AMULET_FULL_ITEM_ID.equals(stack.getItemId())) continue;
                BsonDocument meta = stack.getMetadata();
                if (meta != null && meta.containsKey(METADATA_SOUL_UUID)) {
                    try {
                        String uuidStr = meta.getString(METADATA_SOUL_UUID).getValue();
                        if (soulUuid.equals(java.util.UUID.fromString(uuidStr))) return true;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static Inventory getPlayerInventoryStatic(Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef player) {
        try {
            Class<?> playerComponentClass = Class.forName("com.hypixel.hytale.server.core.entity.entities.Player");
            Method getComponentType = playerComponentClass.getMethod("getComponentType");
            Object playerComponentType = getComponentType.invoke(null);
            Method getComponent = store.getClass().getMethod("getComponent", Ref.class, playerComponentType.getClass());
            Object playerComponent = getComponent.invoke(store, playerRef, playerComponentType);
            if (playerComponent != null) {
                Method getInventory = playerComponentClass.getMethod("getInventory");
                Inventory inv = (Inventory) getInventory.invoke(playerComponent);
                if (inv != null) return inv;
            }
        } catch (Throwable ignored) {}
        try {
            Method getInventory = player.getClass().getMethod("getInventory");
            Inventory inv = (Inventory) getInventory.invoke(player);
            if (inv != null) return inv;
        } catch (Throwable ignored) {}
        for (String methodName : new String[]{"getPlayerInventory", "inventory", "getItemInventory"}) {
            try {
                Method m = player.getClass().getMethod(methodName);
                Object result = m.invoke(player);
                if (result instanceof Inventory) return (Inventory) result;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** Dados do amuleto cheio: UUID da alma e roleIndex (para escolher Horse_Skeleton_Friendly vs Armored). */
    public static final class SoulAmuletData {
        public final java.util.UUID soulUuid;
        public final int roleIndex;

        public SoulAmuletData(java.util.UUID soulUuid, int roleIndex) {
            this.soulUuid = soulUuid;
            this.roleIndex = roleIndex;
        }
    }

    /**
     * Uso do Chifre (Horn): chama a montaria vinculada (teleporte + reativa follow).
     * Deve ser chamado na world thread (ex.: a partir de HornOnUseFilter).
     * Sem vínculo → mensagem "no_bond"; com vínculo → teleportHorseNearPlayer + mensagem ok/fail + som do chifre.
     */
    public void tryHornUse(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null || !playerRef.isValid()) return;
        Ref<EntityStore> horseRef = followService.getBoundHorse(playerRef);
        if (horseRef == null || !horseRef.isValid()) {
            sendConsumedMessage(store, playerRef, "horsefollow.command.call.no_bond");
            return;
        }
        boolean ok = followService.teleportHorseNearPlayer(store, playerRef, horseRef);
        sendConsumedMessage(store, playerRef, ok ? "horsefollow.command.call.ok" : "horsefollow.command.call.fail");
        lastHornUseCompleteTime.put(playerRef, System.currentTimeMillis());
    }

    /** Volume do som do chifre (1.0 = padrão). */
    private static final float HORN_SOUND_VOLUME = 0.75f;

    /** IDs do SoundEvent do chifre (mod). */
    private static final String[] HORN_SOUND_IDS = {
        "SFX/Items/Horn/SFX_Horn_Use",
        "Horn_Sound",
        "SFX_Horn_Use"
    };

    /**
     * Retorna o item ID do slot ativo da barra de utilitários, ou null.
     * Deve ser chamado na world thread.
     */
    public String getUtilityItemId(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null || !playerRef.isValid()) return null;
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) return null;
        Inventory inv = getPlayerInventory(store, playerRef, player);
        if (inv == null) return null;
        try {
            Method getUtilityItem = inv.getClass().getMethod("getUtilityItem");
            Object stack = getUtilityItem.invoke(inv);
            if (stack == null) return null;
            Method getItemId = stack.getClass().getMethod("getItemId");
            return (String) getItemId.invoke(stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Chamado quando o jogador envia Secondary (botão direito ou uso da utility bar).
     * Se o item usado for o Horn (na mão ou na barra de utilitários), executa som + agenda call em 4s.
     * Throttle: o cliente envia muitos Secondary enquanto segura; só reagimos à primeira (uma carga por vez).
     * Deve ser chamado na world thread.
     */
    public void onSecondaryInteraction(Store<EntityStore> store, Ref<EntityStore> playerRef, String packetItemInHandId) {
        if (store == null || playerRef == null || !playerRef.isValid()) return;
        boolean hornInHand = HORN_ITEM_ID.equals(packetItemInHandId);
        String utilityId = getUtilityItemId(store, playerRef);
        boolean hornInUtility = HORN_ITEM_ID.equals(utilityId);
        if (!hornInHand && !hornInUtility) return;
        if (!canUseHorn(playerRef)) return;
        long now = System.currentTimeMillis();
        Long lastStart = lastHornChargeStartTime.get(playerRef);
        if (lastStart != null && (now - lastStart) < HORN_CHARGE_START_THROTTLE_MS) {
            return;
        }
        lastHornChargeStartTime.put(playerRef, now);
        if (plugin != null) {
            Object entityStore = store.getExternalData();
            Ref<EntityStore> ref = playerRef;
            Store<EntityStore> storeRef = store;
            plugin.scheduleHornCall(new java.util.TimerTask() {
                @Override
                public void run() {
                    ItemConsume.runOnWorldThread(entityStore, () -> playHornSound(storeRef, ref));
                }
            }, HORN_SOUND_DELAY_MS);
            plugin.scheduleHornCall(new java.util.TimerTask() {
                @Override
                public void run() {
                    ItemConsume.runOnWorldThread(entityStore, () -> tryHornUse(store, ref));
                }
            }, HORN_CHARGE_MS);
        }
    }

    private static final String HORN_ITEM_ID = "Horn";

    /**
     * Verifica se o jogador pode usar o Chifre (fora do cooldown de 1 s).
     * Pode ser chamado de qualquer thread (leitura do mapa).
     */
    public boolean canUseHorn(Ref<EntityStore> playerRef) {
        if (playerRef == null || !playerRef.isValid()) return false;
        Long last = lastHornUseCompleteTime.get(playerRef);
        if (last == null) return true;
        return (System.currentTimeMillis() - last) >= HORN_USE_COOLDOWN_MS;
    }

    /**
     * Toca o som do chifre na posição do jogador (junto com o carregamento).
     * Público para ser chamado pelo HornOnUseFilter ao iniciar a carga.
     */
    public void playHornSound(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null || !playerRef.isValid()) return;
        try {
            Integer index = getHornSoundEventIndex();
            if (index == null || index < 0) return;
            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform == null) return;
            var pos = transform.getPosition();
            SoundUtil.playSoundEvent3dToPlayer(playerRef, index.intValue(), SoundCategory.SFX, pos.getX(), pos.getY(), pos.getZ(), HORN_SOUND_VOLUME, 1.0f, store);
        } catch (Throwable t) { }
    }

    private static Integer getHornSoundEventIndex() {
        for (String id : HORN_SOUND_IDS) {
            Integer idx = tryGetSoundEventIndex(id);
            if (idx != null && idx >= 0) return idx;
        }
        return null;
    }

    /** Volume do som de feed (1.0 = padrão). Aumentado para ficar mais audível. */
    private static final float FEED_SOUND_VOLUME = 1.5f;

    /** IDs do SoundEvent de consumo (feed). Primeiro é o do mod; demais são fallbacks. */
    private static final String[] FEED_CONSUME_SOUND_IDS = {
        "Consume_Bread_Sound",
        "SFX/Items/Consume_Bread_Stereo_01",
        "SFX_Items_Consume_Bread_Stereo_01"
    };

    /**
     * Toca o som de consumo (pão/feed) na posição do jogador, após feed com sucesso.
     * Usa SoundUtil.playSoundEvent3dToPlayer (API Hytale). Índice obtido por reflexão.
     */
    private void playFeedConsumeSound(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null || !playerRef.isValid()) return;
        try {
            Integer index = getSoundEventIndex();
            if (index == null || index < 0) return;
            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform == null) return;
            var pos = transform.getPosition();
            SoundUtil.playSoundEvent3dToPlayer(playerRef, index.intValue(), SoundCategory.SFX, pos.getX(), pos.getY(), pos.getZ(), FEED_SOUND_VOLUME, 1.0f, store);
        } catch (Throwable t) { }
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
    static Integer tryGetSoundEventIndex(String soundEventId) {
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
        } catch (Throwable t) { }
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
        } catch (Throwable t) { }
    }

    /**
     * Limpa todos os estados (útil para shutdown).
     */
    public void clearAll() {
        lastScheduled.clear();
        lastFeedHandledByFKey.clear();
        lastHorseFeedQuantity.clear();
        lastRamFeedQuantity.clear();
        lastHornUseCompleteTime.clear();
        lastHornChargeStartTime.clear();
    }
}
