# Adaptação do Feed à lógica do Rekindle Embers (RunOn)

## 1. O que o Rekindle Embers faz

No **Relatorio_Rekindle_Embers.md**, o item usa **Charging** e, ao completar 0.8s, em vez de consumir direto, roda:

- **Type**: `RunOnBlockTypes`
- **BlockSets**: `["Necromancy_Bones"]` (conjunto de blocos: pilhas de ossos, caveiras)
- **Range**: `5` blocos
- **MaxCount**: `5`

**Se encontrar blocos no alcance** → executa a cadeia (SpawnNPC, DestroyBlock, **ModifyInventory -1**).  
**Se não encontrar** → executa o ramo **Failed** (só animação "Interact", **sem consumo**).

Ou seja: o item **só é consumido** quando a condição (blocos no alcance) é satisfeita. O motor do jogo faz essa checagem no JSON.

---

## 2. Equivalente desejado para Horse_Feed / Ram_Feed

A mesma ideia para o feed seria:

- Ao completar **2.0s** do charge, em vez de `ModifyInventory` direto, rodar algo como **RunOnNPCTypes** (ou RunOnRoles / RunOnEntityTypes):
  - **Horse_Feed**: só passar se houver NPC com role **Horse** ou **Horse_Friendly** no alcance (ex.: 3 blocos).
  - **Ram_Feed**: só passar se houver NPC com role **Ram** ou **Ram_Friendly** no alcance.
- **Range**: 3 blocos (como no config `feedBindRange`).
- **Se encontrar NPC no alcance** → executar **ModifyInventory -1** (e o bind continua em código ao detectar consumo).
- **Se não encontrar** → executar **Failed** (animação, **sem consumo**).

Assim o item **só seria reduzido** quando houver alvo próximo, igual ao Rekindle Embers com blocos.

---

## 3. Problema: existe RunOn para NPCs no engine?

Nos assets analisados (Rekindle_Embers) só aparece **RunOnBlockTypes**. Não há exemplo de:

- `RunOnNPCTypes`
- `RunOnRoles`
- `RunOnEntityTypes`
- ou conjunto de NPCs (tipo BlockSets, mas para roles/entidades).

Ou seja: não sabemos se o Hytale expõe em JSON um “RunOn” para entidades/NPCs. Pode ser que:

- exista com outro nome (ex.: `RunOnRoles`, `RunOnEntityTypes`);
- exista com estrutura diferente (ex.: referência a um “NPC Set” em outro JSON);
- ou não exista e a condição “só consumir se tiver alvo” tenha de ser feita só em código (como hoje: consumir sempre e devolver com refund se não houver target).

---

## 4. O que podemos fazer

### Opção A – Tentar RunOn para NPCs no JSON (por analogia ao Rekindle Embers)

Criar no **Horse_Feed_Charge** (e equivalente no Ram_Feed_Charge) uma estrutura análoga a `RunOnBlockTypes`:

- No passo **2.0** do Charging, em vez de um único `ModifyInventory`, usar algo como:
  - **Type**: `RunOnNPCTypes` ou `RunOnRoles` (nome a confirmar no engine/docs).
  - **RoleIds** ou **RoleSets**: `["Horse", "Horse_Friendly"]` (Horse_Feed) ou `["Ram", "Ram_Friendly"]` (Ram_Feed).
  - **Range**: `3`.
  - **Interactions**: ao passar → Serial com `ModifyInventory -1` e animação.
  - **Failed**: Simple (só animação, sem consumo).

Se o engine reconhecer esse tipo e a estrutura, o item **só será reduzido** quando houver montaria no alcance, e o código Java continua fazendo o bind ao detectar consumo. Se o engine não reconhecer, o JSON pode falhar ao carregar ou o passo 2.0 pode não rodar; aí precisamos voltar à Opção B.

### Opção B – Manter lógica só em código (atual)

- **ModifyInventory** continua no passo 2.0 (o jogo sempre consome ao completar o charge).
- O plugin detecta consumo (diminuição de quantidade) e:
  - se houver **target** no alcance → faz bind (e não devolve nada).
  - se **não** houver target → faz **refund** (devolve 1) e mensagem “nenhum alvo no alcance”.

Funciona, mas o item “some” um frame e depois volta se não houver alvo; idealmente o consumo nem aconteceria, como no Rekindle Embers.

---

## 5. Resumo

| Rekindle Embers (blocos) | Feed desejado (NPCs) |
|--------------------------|----------------------|
| RunOnBlockTypes          | RunOnNPCTypes / RunOnRoles (?) |
| BlockSets: ["Necromancy_Bones"] | RoleIds: ["Horse","Horse_Friendly"] ou ["Ram","Ram_Friendly"] |
| Range: 5                 | Range: 3 |
| Passou → ModifyInventory -1 | Passou → ModifyInventory -1 (+ bind em código) |
| Falhou → Failed (sem consumo) | Falhou → Failed (sem consumo) |

**Teste RunOnNPCTypes (revertido)**: Foi tentada a **Opção A** nos JSONs com **Type**: `RunOnNPCTypes`, **RoleIds**, **Range**: 3. O engine **não reconheceu** o tipo.

**Erro no log (AssetStore|Interaction)**:
- `Failed to decode asset: *Horse_Feed_Charge_Next_2.0` e `*Ram_Feed_Charge_Next_2.0`
- **Causa**: `com.hypixel.hytale.codec.lookup.ACodecMapCodec$UnknownIdException: No codec registered with for 'Type': RunOnNPCTypes`

Ou seja: não existe **codec** registrado para o tipo `RunOnNPCTypes` no engine (ao contrário de `RunOnBlockTypes`, que é suportado). Os JSONs foram **revertidos** para a estrutura anterior (passo 2.0 = `ModifyInventory` direto).

**Comportamento atual (Opção B)**: O jogo consome 1 unidade ao completar o charge (ModifyInventory no JSON). O plugin detecta a diminuição de quantidade; se houver target no alcance (3 blocos), faz bind; se não houver, faz **refund** (devolve 1) e mensagem "nenhum alvo no alcance".
