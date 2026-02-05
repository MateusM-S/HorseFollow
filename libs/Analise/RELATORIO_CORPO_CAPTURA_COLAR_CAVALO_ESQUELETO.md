# Relatório: corpo ao morrer, caixa de captura e colar do cavalo esqueleto

**Objetivo:** Localizar onde ficam a mecânica do “corpo” ao matar um mob e a da caixa de captura, e esboçar como fazer um colar que capture a “alma” do cavalo esqueleto (domar via corpo).

---

## 1. Onde está a mecânica do corpo (cadáver)

No **HytaleServer.jar** a morte e o cadáver são tratados no módulo de dano:

| Classe | Caminho (no JAR) | Função provável |
|--------|-------------------|-----------------|
| **DeathSystems** | `com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems` | Orquestra tudo na morte |
| **DeathSystems$CorpseRemoval** | (inner class) | Remove o cadáver do mundo |
| **DeferredCorpseRemoval** | `...damage.DeferredCorpseRemoval` | **Atrasa** a remoção do cadáver (corpo fica alguns segundos) |
| **DeathSystems$DeathAnimation** | (inner class) | Animação de morte |
| **DeathSystems$SpawnedDeathAnimation** | (inner class) | Spawn da animação de morte |
| **DeathComponent** | `...damage.DeathComponent` | Componente ECS de “entidade morta” |
| **DeathSystems$RunDeathInteractions** | (inner class) | Roda interações configuradas na morte |

Conclusão: o “corpo” é a **mesma entidade** em estado morto; a remoção é **adiada** por `DeferredCorpseRemoval`. Não existe um “entity type” separado “corpse” — é a entidade viva que recebe estado de morte e depois é removida.

---

## 2. Onde está a caixa de captura

Também no **HytaleServer.jar**:

| Classe | Caminho | Função |
|--------|---------|--------|
| **UseCaptureCrateInteraction** | `com.hypixel.hytale.builtin.adventure.farming.interactions.UseCaptureCrateInteraction` | Interação de **usar** a caixa de captura (provavelmente “mirar em entidade” e capturar) |
| **CapturedNPCMetadata** | `com.hypixel.hytale.server.npc.metadata.CapturedNPCMetadata` | Metadados do NPC capturado (role, estado, etc.) armazenados no item ou em dados de mundo |

O “bug” que você descreveu: matar o animal e usar a caixa **no corpo** antes do `DeferredCorpseRemoval` remover faz o jogo tratar como captura; ao soltar, o animal volta vivo. Ou seja, a **UseCaptureCrateInteraction** provavelmente aceita alvo “entity” (vivo ou em estado de morte/cadáver), e o **CapturedNPCMetadata** guarda o necessário para recriar o NPC.

Para o colar do cavalo esqueleto, a ideia seria análoga: uma interação “usar item em entidade” que só vale em **cadáver** de cavalo esqueleto e que grava algo como “alma” (UUID + dados mínimos) no item.

---

## 3. Sobre observar kill/captura no log

No **latest_server.log** que você tem, não aparecem linhas específicas de “kill” ou “capture” durante gameplay — só carregamento de módulos (DamageModule, NPC, etc.), avisos de JSON e um stack trace de `Player.remove`. Ou seja:

- O jogo **não** loga por padrão “player matou entidade X” ou “player capturou entidade Y” nesse arquivo.
- Para **observar** kill/captura você pode:
  1. **Plugin:** inscrever-se em eventos de morte/remoção de entidade e de uso de item em entidade (se a API expuser isso) e fazer `log.info(...)` no seu mod.
  2. **Debug do Hytale:** se existir modo debug ou flags de log do DamageModule/NPC/Farming, ativar e gerar um novo log enquanto mata e captura.

Ou seja: **sim**, dá para tentar observar no log, mas só depois de **adicionar** logs no seu plugin (eventos de morte/uso em entidade) ou de descobrir se o Hytale tem log de debug para esses sistemas.

---

## 4. Pesquisa no HytaleServer e nos assets

- **HytaleServer.jar:**  
  - Morte/cadáver: `DeathSystems`, `DeferredCorpseRemoval`, `DeathComponent`.  
  - Captura: `UseCaptureCrateInteraction`, `CapturedNPCMetadata`.  
  Não há nomes de classe “Corpse” ou “Ragdoll”; o corpo é a entidade com estado de morte e remoção adiada.

- **Assets (JSON de item/NPC):**  
  No projeto não há extração do item “Capture Crate” nem do cadáver. Para achar:
  - **Capture Crate:** no Assets.zip do jogo, procurar por `Capture_Crate` ou `UseCaptureCrate` em `Server/Item/` e em `Server/Item/Interactions/` (ou equivalente).
  - **Cadáver:** não é um asset separado; é o mesmo NPC/entidade com comportamento de morte (DeathSystems) e remoção adiada.

- **Documentação (hytalemodding.dev):**  
  Lista de entidades inclui **Horse_Skeleton** e **Horse_Skeleton_Armored** — são os cavalos esqueleto que você quer usar no colar.

---

## 5. Ideia do colar do cavalo esqueleto (resumo)

- **Captura da “alma”:**  
  Matar o cavalo esqueleto → usar o **colar no corpo** (antes de sumir).  
  Isso exige uma interação tipo “RunOnEntity” ou “UseOnEntity” restrita a:
  - entidade em estado de morte (DeathComponent / cadáver), e  
  - role/entity type = cavalo esqueleto (ex.: `Horse_Skeleton`).  
  Ao usar: trocar o item para “colar preenchido” e guardar no item (ou em dados do mod) o **UUID do cavalo** (e talvez role/ID) como “alma”.

- **Invocação:**  
  Usar o colar preenchido em um **bloco** (ou no ar?) → SpawnNPC `Horse_Skeleton` (ou variante amigável), com animação no estilo Rekindle Embers, e vincular esse spawn ao UUID salvo no colar (só um cavalo por colar no mundo; dono = portador do colar).

- **Morte do cavalo invocado:**  
  Ao morrer, não criar novo cadáver “capturável”; fazer o cavalo “voltar ao colar” (remover entidade + marcar colar como “com alma de novo” ou manter o mesmo estado “preenchido” para nova invocação).

- **Independência do HorseFollow:**  
  O vínculo do cavalo esqueleto com o colar seria **separado** do sistema Horse/Ram do HorseFollow (outro mapa de “dono” ou “colar → UUID”), para não interferir em bind/unbind das outras montarias.

---

## 6. Próximos passos sugeridos

1. **API do servidor:**  
   Ver na documentação ou no JAR se existe:
   - evento “entity death” / “entity removed” (ex.: `EntityRemoveEvent` já existe no pacote `events/entity`);
   - interação de item “RunOnEntity” / “UseOnEntity” / “TargetEntity” (no Rekindle você já viu `TargetEntityPart` em efeitos; falta ver se há **teste** “RunOnEntityTypes” ou “UseOnDeadEntity”).

2. **Assets:**  
   Extrair do Assets.zip o item **Capture Crate** e sua **RootInteraction** / interação (ex.: `UseCaptureCrateInteraction`) para ver exatamente como eles miram em entidade (viva/cadáver) e como usam **CapturedNPCMetadata**.

3. **Plugin:**  
   Registrar um listener para morte de entidade (e, se existir, “uso de item em entidade”) e logar no seu `latest_server.log` para **observar** na prática o que acontece ao matar e ao usar a caixa no corpo.

4. **Colar:**  
   Desenhar o item “colar vazio” e “colar com alma”, a interação “usar em cadáver de Horse_Skeleton” e a interação “usar colar para invocar”, usando o máximo possível de JSON (RunOn*, SpawnNPC, etc.) e o mínimo de Java só onde a API não expuser (ex.: persistência UUID no item, “voltar ao colar” na morte).

### Interações que miram em entidade (HytaleServer.jar)

No módulo de interação existem:

| Classe | Caminho | Função provável |
|--------|---------|-----------------|
| **UseEntityInteraction** | `...interaction.config.client.UseEntityInteraction` e `...protocol.UseEntityInteraction` | **Usar item em entidade** (candidato para “usar colar no corpo”) |
| **SelectInteraction$HitEntity** | `...interaction.config.none.SelectInteraction$HitEntity` | Entidade “acertada” na seleção |
| **RemoveEntityInteraction** | `...interaction.config.none.simple.RemoveEntityInteraction` | Remover entidade |
| **DamageEntityInteraction** | `...interaction.config.server.DamageEntityInteraction` | Causar dano em entidade |
| **TargetEntityEffect** | `...interaction.config.server.combat.TargetEntityEffect` | Aplicar efeito em entidade |

A **UseEntityInteraction** é o candidato natural para “usar colar no cadáver” — a caixa de captura provavelmente usa esse tipo de interação. Falta ver no Assets ou na API se o JSON do item permite restringir a **entidade morta** e ao **role/entity type** (ex.: Horse_Skeleton).

---

Se quiser, o próximo passo pode ser: (a) esboçar o listener de morte/captura para log, ou (b) extrair/analisar o JSON do Capture Crate no Assets para ver como ele usa UseEntityInteraction.
