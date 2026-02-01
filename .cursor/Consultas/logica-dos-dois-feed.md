# Lógica exata dos 2 _Feed (Horse_Feed e Ram_Feed)

## Visão geral

O vínculo (bind) com a montaria pode ser feito por **dois caminhos**:

1. **Charge (segurar uso)** — o jogador usa o item até completar a interação; o engine **consome 1** do item; nosso tick **detecta a diminuição** e faz bind ou refund.
2. **Tecla F (Use)** — um filtro de packet **intercepta** o Use com o feed na mão; **cancela** o packet (evita montar); na world thread: acha target, **consome 1** nós mesmos, bind e som.

Em ambos, a regra é: **target no alcance → bind + mensagem de sucesso**; **sem target → mensagem de erro** (e no charge ainda **devolvemos 1** item).

---

## 1. Charge (Horse_Feed_Charge / Ram_Feed_Charge)

### JSON (Interactions)

- **Tipo:** `Charging`
- **Efeito:** animação `Consume`, `ClearAnimationOnFinish: true`
- **Next:** por progresso da carga:
  - `0` → Simple 0.1s
  - `1.0` → Simple 0s
  - `2.0` → **ModifyInventory**, `AdjustHeldItemQuantity: -1` (engine remove 1 do item na mão), depois Simple 0.2s com animação `Interact`
- **Failed:** Simple 0.1s

Ou seja: ao **completar** a carga (2.0), o **engine** reduz em 1 a quantidade do item segurado. O mod **não** é chamado nesse momento; só vemos o efeito no inventário.

### Link item ↔ interaction

- O engine associa o item ao interaction pelo **nome**: `Horse_Feed` → `Horse_Feed_Charge.json`, `Ram_Feed` → `Ram_Feed_Charge.json` (convenção `ItemId_Charge`).

### Lado Java (ItemConsume.tick + tryNotifyConsumed)

- **Tick** (a cada 100 ms, mesmo timer do FollowService):
  - Para cada jogador online, a cada `CHECK_INTERVAL_TICKS` (2), chama `checkPlayerConsuming(store, playerRef)` na **world thread**.

- **checkPlayerConsuming**:
  - Obtém inventário do jogador.
  - Para **Horse_Feed** e **Ram_Feed**:
    - Lê a **quantidade atual** do item (getItem/getItemInHand/findItem, etc.).
    - Compara com a **última quantidade** guardada em `lastHorseFeedQuantity` / `lastRamFeedQuantity`.
    - Se a quantidade **diminuiu** → chama `tryNotifyConsumed(store, playerRef, horseFeed)` (true = Horse_Feed, false = Ram_Feed).
  - Atualiza o mapa de “última quantidade” para o próximo tick.

- **tryNotifyConsumed** (só quando detectou “consumo” por diminuição):
  - Se há pouco tempo o bind foi feito pela **tecla F** (`lastFeedHandledByFKey` dentro de `FEED_FKEY_COOLDOWN_MS` 2500 ms) → **retorna sem fazer nada** (evita mensagem “no_target” e refund após um bind bem-sucedido pelo F).
  - Throttle: se já processou este jogador há menos de 2000 ms → retorna.
  - `followService.findTargetMount(store, playerRef, range, horseFeed)`:
    - **Target válido** (cavalo/carneiro no alcance, não vinculado, role Horse ou Ram) → `followService.bind(playerRef, target)` + mensagem de sucesso.
    - **Sem target** → `refundHeldItem(store, playerRef, itemId)` (devolve 1) + mensagem “no_target”.

Resumo charge: **engine consome 1** → no próximo tick **detectamos a diminuição** → **tryNotifyConsumed** faz bind ou refund + mensagem.

---

## 2. Tecla F (Use) — FeedOnFKeyFilter

### Packet

- **ID:** 290 — `SyncInteractionChains`
- **Tipo de interação:** `InteractionType.Use`
- **itemInHandId:** `Horse_Feed` ou `Ram_Feed`

### Comportamento do filtro

- Se o packet for Use com `Horse_Feed` ou `Ram_Feed`:
  - Obtém `entityRef`, `store`, `entityStore` do jogador.
  - **Agenda na world thread:** `ItemConsume.tryFeedFromFKey(store, entityRef, horseFeed)` (true = Horse_Feed, false = Ram_Feed).
  - **Retorna `true`** → o packet é **cancelado** (o engine não processa; assim o jogador **não monta** ao apertar F com o feed).

### tryFeedFromFKey (world thread)

- `followService.findTargetMount(store, playerRef, range, horseFeed)`:
  - **Sem target** → só envia mensagem “no_target”; **não** mexe no inventário (não consome, não devolve).
  - **Com target**:
    - `consumeOneHeldItem(store, playerRef, itemId)` — remove 1 do item na mão (hotbar ativa), `inventory.markChanged()`.
    - `followService.bind(playerRef, target)`.
    - Marca `lastFeedHandledByFKey.put(playerRef, now)` para o tick **não** tratar como “consumo por charge” e mandar “no_target” / refund.
    - `playFeedConsumeSound(store, playerRef)`.
    - Mensagem de sucesso (bind ok).

Resumo F: **interceptamos Use** → **cancelamos** o packet → na world thread: **só consome 1 se houver target**; **bind + som + mensagem**.

---

## Diferenças entre os dois caminhos

| Aspecto              | Charge (segurar uso)                    | Tecla F (Use)                          |
|----------------------|------------------------------------------|----------------------------------------|
| Quem consome o item  | Engine (ModifyInventory -1)              | Mod (consumeOneHeldItem)               |
| Quando detectamos    | No tick seguinte (diminuição de quantidade) | Imediato (na world thread)             |
| Sem target           | Refund 1 + mensagem “no_target”           | Só mensagem “no_target”                |
| Evitar duplicata     | lastFeedHandledByFKey (não refund/msg após F) | lastFeedHandledByFKey (tick não trata) |
| Som                  | Não (tick não toca som)                  | Sim (playFeedConsumeSound)             |
| Packet               | Não interceptado                        | Cancelado (evita montar)               |

---

## Arquivos envolvidos

- **JSON:**  
  `Server/Item/Items/Horse_Feed.json`, `Ram_Feed.json`  
  `Server/Item/Interactions/Horse_Feed_Charge.json`, `Ram_Feed_Charge.json`
- **Java:**  
  `ItemConsume.java` (tick, checkPlayerConsuming, tryNotifyConsumed, tryFeedFromFKey, consumeOneHeldItem, refundHeldItem, getPlayerInventory, getItemQuantity, playFeedConsumeSound, sendConsumedMessage)  
  `FeedOnFKeyFilter.java` (intercepta Use com Horse_Feed/Ram_Feed, agenda tryFeedFromFKey, retorna true)  
  `HorseFollowPlugin.java` (registra filtro, timer com itemConsume.tick)  
  `FollowService.java` (findTargetMount, bind, getFeedBindRange)
