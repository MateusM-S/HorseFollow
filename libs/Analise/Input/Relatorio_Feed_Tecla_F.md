# Relatório: Feed pela tecla F em vez do botão direito (Secondary)

**Data:** 29 de Janeiro de 2026  
**Projeto:** HorseFollow  
**Objetivo:** Avaliar a mudança da interação de feed do botão direito do mouse (Secondary) para a tecla de interação **F**, bloqueando o “montar” quando o jogador estiver com o item de feed na mão (como o jogo já faz com o modo pânico do cavalo).

---

## 1. Situação atual

- **Feed (vínculo):** o jogador usa **Horse_Feed** ou **Ram_Feed** com **botão direito** (Secondary): o item entra em charge, ao terminar o consumo é detectado pelo plugin (`ItemConsume`) e é feito o bind na montaria mais próxima no alcance (`feedBindRange`).
- **Montar:** a mesma montaria tem interação pela tecla **F** (`HasInteracted` no `Template_Animal_Tamed.json`), que dispara a ação **Mount**.

O desafio: **F** já está associado a “montar”. A ideia é: **com o feed na mão, F = dar feed (bind); sem feed na mão, F = montar.**

---

## 2. O que o JSON do Hytale permite (Template_Animal_Tamed)

- **Harvest:** usa `InteractionContext` (ferramenta na mão). A ação de colheita é disparada por **left-click com o contexto** (ex.: tesoura). O prompt da tecla F pode ficar oculto (`ShowPrompt: false`) e a lógica de “só interagir com a ferramenta certa” vem do **contexto**.
- **Mount:** usa apenas `HasInteracted` (tecla F), **sem** verificação de item na mão. Não existe no template um “InteractionContext para Mount” que desabilite o Mount quando o jogador tem um certo item.

Conclusão: **só com JSON não dá para** “quando tem feed na mão, F = feed em vez de montar”. O motor não expõe uma condição do tipo “bloquear Mount se HeldItem = Horse_Feed/Ram_Feed”. É preciso lógica no **plugin (Java)**.

---

## 3. Abordagem recomendada: interceptar o packet da tecla F

A documentação de modding (hytalemodding.dev) indica:

- A tecla F não chega como “tecla bruta”; o cliente envia **packets** que descrevem a ação (ex.: interagir com a entidade em foco).
- O packet relevante é **SyncInteractionChains** (ID **290**), tratado pelo `GamePacketHandler`.
- **PacketAdapters.registerInbound(PlayerPacketFilter)** permite **cancelar** um packet (retornar `true` = o servidor não processa).
- Ao cancelar o packet de interação quando (jogador com feed na mão + alvo é montaria), o servidor **não** executa a ação Mount; aí o plugin pode, na world thread, executar **feed (consumir 1 item + bind)**.

Fluxo proposto:

1. Registrar um **PlayerPacketFilter** para o packet **290** (SyncInteractionChains).
2. Quando o packet for do tipo que corresponde à **tecla F** (ex.: `InteractionType.Use` ou o que for usado para “interagir com entidade” — ver nota abaixo):
   - Obter o **playerRef** do handler (ex.: `GamePacketHandler.getPlayerRef()`).
   - Na **world thread** (`world.execute`): verificar item na mão.
     - Se for **Horse_Feed** ou **Ram_Feed**:
       - Calcular o alvo com **findTargetMount(store, playerRef, feedBindRange, horseFeed)** (já existente).
       - Se houver **target** válido: **cancelar o packet** (retornar `true`), consumir 1 unidade do item, chamar **followService.bind(playerRef, target)** e enviar mensagem de sucesso.
       - Se **não** houver target no alcance: cancelar o packet, **não** alterar o inventário (nenhuma redução do item) e mostrar mensagem “sem montaria no alcance”.
     - Se **não** for feed: **não** cancelar (retornar `false`); o Mount normal ocorre.
3. Garantir que consumo de item e bind só rodem dentro de `world.execute`, por segurança do ECS.

Assim, **só bloqueamos o “montar” quando o jogador está com o item de feed na mão**, e nesse caso a ação vira “dar feed” (bind).

---

## 4. Pontos a resolver na implementação

### 4.1) Tipo de interação da tecla F

A documentação diz que **InteractionType.Use (5)** é “when a player uses an item”. Pode ser que “F em entidade” use outro tipo ou que Use seja genérico (item ou entidade). Na implementação, é necessário:

- Usar as classes do `HytaleServer.jar`: `com.hypixel.hytale.protocol.packets.*` (ex.: `SyncInteractionChains`), `InteractionType`.
- Inspecionar os valores de `SyncInteractionChain` (ex.: `interactionType`) quando o jogador pressiona F em um cavalo e, se necessário, tratar mais de um tipo (ex.: Use e outro específico de “interact with entity”).

### 4.2) Identificação do “alvo” da interação

- **Opção A:** O packet 290 pode trazer o **entityId** (ou equivalente) do alvo. Nesse caso, no plugin: resolver `Ref<EntityStore>` a partir do ID e verificar se é Horse/Ram (role).
- **Opção B:** Se o packet não trouxer alvo, usar a mesma regra do feed atual: **findTargetMount(store, playerRef, feedBindRange, horseFeed)** — “montaria mais próxima no alcance”. É consistente com o comportamento atual (dar feed na mais próxima) e evita dependência de “reticle” no servidor.

Recomendação: implementar primeiro com **Opção B** (findTargetMount). Se depois for possível obter o entidade-alvo do packet, pode-se restringir ao “cavalo em que o jogador está olhando” para ficar mais intuitivo.

### 4.3) PlayerPacketFilter e world thread

- O filter é chamado na rede; não se pode acessar ECS/inventário diretamente.
- É preciso **decidir no filter** só com dados do packet (ex.: tipo de interação) se “poderia ser feed”. Se sim, **cancelar o packet** e **agendar** uma tarefa para a **world thread** que:
  - Verifica item na mão (Horse_Feed / Ram_Feed),
  - Chama findTargetMount,
  - Consome 1 item, chama bind, envia mensagem (ou refund + mensagem).

Ou seja: o filter retorna `true` (cancela) quando há indício de “F em entidade” **e** vamos tratar como feed; o trabalho pesado (inventário, store, bind) fica na world thread.

### 4.4) Referências de API (JAR) — pesquisa no HytaleServer.jar

Pesquisa feita com `jar tf` e `javap -cp libs/HytaleServer.jar`:

- **PacketAdapters:** `com.hypixel.hytale.server.core.io.adapter.PacketAdapters`
  - `registerInbound(PlayerPacketFilter)` → retorna `PacketFilter` (para cancelar: o filter retorna `true`).
  - `registerInbound(PlayerPacketWatcher)` também disponível.
- **SyncInteractionChains:** `com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains`
  - Implementa `Packet`; campo **`updates`** (tipo `SyncInteractionChain[]`).
- **SyncInteractionChain:** `com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain`
  - Campos relevantes: **`itemInHandId`** (String), **`interactionType`** (InteractionType), além de `activeHotbarSlot`, `chainId`, `data`, `state`, etc.
  - Podemos checar `itemInHandId == "Horse_Feed"` ou `"Ram_Feed"` e `interactionType == Use` para detectar “F com feed na mão”.
- **InteractionType:** `com.hypixel.hytale.protocol.InteractionType` (enum)
  - Valores: `Primary`, `Secondary`, `Ability1`–`Ability3`, **`Use`**, `Pick`, `Pickup`, `CollisionEnter`/`Leave`/`Collision`, `EntityStatEffect`, `SwapTo`/`SwapFrom`, `Death`, `Wielding`, `ProjectileSpawn`/`Hit`/`Miss`/`Bounce`, `Held`, `HeldOffhand`, `Equipped`, `Dodge`, `GameModeSwap`.
  - A doc de modding associa **Use** à tecla de interação (F). Confirmar em jogo se “F em entidade” envia `Use`.
- **PacketHandler:** `com.hypixel.hytale.server.core.io.PacketHandler` — para obter `PlayerRef`/player do handler (ex.: `GamePacketHandler.getPlayerRef()`), ver na doc ou no JAR a classe concreta que trata o packet 290.

Se alguma classe ou método tiver nome diferente na build atual do servidor, ajustar com base no JAR disponível.

### 4.5) Assets — redução do item no feed atual

Nos assets do mod:

- **Horse_Feed_Charge.json** e **Ram_Feed_Charge.json** (`src/main/resources/Server/Item/Interactions/`):
  - Tipo `Charging`; em **`Next."2.0"`** há `ModifyInventory` com **`AdjustHeldItemQuantity: -1`**.
  - Ou seja, o **engine** reduz 1 do item na mão quando o charge de 2s termina — independente de haver target ou não. O plugin então detecta a queda de quantidade e faz bind ou refund.
- Com a **tecla F**, não disparamos esse Charging na interação “F em montaria”; o consumo do item fica a cargo do plugin **só quando** houver target e fizermos o bind. Assim, quando não houver “gente perto”, não há redução do item.

---

## 5. Comportamento desejado (resumo)

| Situação                         | Tecla F              | Resultado                                      |
|----------------------------------|----------------------|-----------------------------------------------|
| Sem feed na mão, olhando p/ cavalo| F                    | Montar (comportamento normal do jogo)         |
| Com Horse_Feed na mão, cavalo no alcance | F | **Não** montar; consumir 1 Horse_Feed e **bind** |
| Com Ram_Feed na mão, carneiro no alcance | F | **Não** montar; consumir 1 Ram_Feed e **bind**   |
| Com feed na mão, **sem** montaria no alcance | F | **Não** montar; **nenhuma redução do item**; só mensagem “sem alvo”   |

Assim, o “montar” fica bloqueado **apenas** quando o jogador está com o item de feed na mão, de forma análoga ao modo pânico (comportamento condicionado ao estado do jogador).

### 5.1) Redução do item só quando houver bind

Com a abordagem da **tecla F**, o plugin controla quando o item é consumido:

- **Com target no alcance:** removemos 1 do item na mão e fazemos o bind (como hoje).
- **Sem target no alcance:** **não** alteramos o inventário — não há redução e não é necessário refund. Apenas mostramos a mensagem “sem montaria no alcance”.

Isso é diferente do fluxo atual com **botão direito** (Secondary): no `Horse_Feed_Charge` / `Ram_Feed_Charge` o engine executa `ModifyInventory` com `AdjustHeldItemQuantity: -1` ao terminar o charge (2.0s), ou seja, o item é reduzido **antes** de sabermos se há target; aí o plugin devolve 1 (refund) se não houver alvo. Com F, como interceptamos o packet **antes** de qualquer consumo do engine, só removemos 1 quando de fato fizermos o bind — assim **dá para tirar a redução do item quando não achar gente perto**.

---

## 6. Próximos passos sugeridos

1. **Confirmar no JAR** os nomes e campos de `SyncInteractionChains`, `SyncInteractionChain` e `InteractionType`, e qual tipo corresponde à tecla F em entidade.
2. **Implementar** um `PlayerPacketFilter` para o packet 290 no `HorseFollowPlugin.setup()`, com a lógica acima (cancelar quando for “F + feed na mão” e agendar feed na world thread).
3. **Manter** o fluxo atual de feed por **Secondary** (botão direito) se quiser: ambos podem coexistir (F e right-click dão feed quando há feed na mão e alvo válido). Ou remover o uso por Secondary e deixar só F, conforme preferência do design.
4. **Testar** em jogo: F com e sem feed, com e sem montaria no alcance, e com Horse_Feed vs Ram_Feed.

---

## 7. Referências

- **Relatório anterior:** `Relatorio_Interacoes_Montaria.md` (InteractionInstruction, HasInteracted, Mount, HarvestInteractionContext).
- **Template:** `src/main/resources/Server/NPC/Roles/_Core/Templates/Template_Animal_Tamed.json` (InteractionInstruction, bloco IsMountable).
- **Doc modding:** [Player Input Guide](https://hytalemodding.dev/en/docs/guides/plugin/player-input-guide), [Listening to Packets](https://hytalemodding.dev/en/docs/guides/plugin/listening-to-packets), [Client-to-Server Packets](https://hytalemodding.dev/en/docs/guides/plugin/client-inputs-reference) (packet 290 = SyncInteractionChains).
- **Código atual:** `ItemConsume.java` (detecção de consumo por quantidade), `FollowService.findTargetMount`, `FollowService.bind`.
