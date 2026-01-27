## Relatório técnico (didático) — `Weapon_Spellbook_Rekindle_Embers`

**Fonte analisada**: `libs/Assets.zip`  
**Pasta de extração (regras do workspace)**: `libs/Analise/analises/Rekindle_Embers/`  

### 1) O que esse item faz (efeito “em jogo”)

Apesar do nome que aparece no jogo (“grimoire: Rekindle Embers”), a definição do item mostra que ele é, na prática, um **grimório/feitiço de necromancia** que:

- **“Ressuscita”/invoca** um NPC aliado do tipo **`Risen_Knight`**;
- **Somente funciona** quando existem certos **blocos de ossos** próximos (pilhas de ossos / caveiras);
- **Consome 1 unidade do item** a cada invocação (o item é **stackável** e funciona como consumível).

### 2) Onde isso está definido (arquivo principal)

Arquivo do item:

- `Server/Item/Items/Weapon/Spellbook/Weapon_Spellbook_Rekindle_Embers.json`

Pontos importantes desse arquivo:

- **MaxStack**: `5` (o item empilha até 5)
- **Tags**: `Type=Weapon`, `Family=Spellbook`
- **Inputs**: a interação de **Primary** e **Secondary** é a mesma (mesma mecânica nos dois botões).

### 3) Como funciona a mecânica (passo a passo)

O item implementa a mecânica inteira via `Interactions.Primary` e `Interactions.Secondary`.

#### 3.1) Ao segurar o botão (carregar)

Tipo: `Charging`

Efeitos enquanto carrega:

- Toca o som **`SFX_Skeleton_Mage_Spellbook_Charge`**
- Toca a animação de item **`CastHurlCharging`**
- Permite segurar indefinidamente (`AllowIndefiniteHold: true`)
- Reduz a velocidade horizontal (`HorizontalSpeedMultiplier: 0.8`)
- Cancela se trocar de item (`CancelOnItemChange: true`)

#### 3.2) Se soltar cedo (antes de 0.8s)

Em `Next."0"`:

- Executa `Simple` com `ItemAnimationId: "Interact"`
- **Não invoca nada**

#### 3.3) Se manter carregado por 0.8s

Em `Next."0.8"`:

Tipo: `RunOnBlockTypes`

Parâmetros:

- **BlockSets**: `[ "Necromancy_Bones" ]`
- **Range**: `5`
- **MaxCount**: `5`

O que acontece ao “passar” no teste de blocos:

- Executa uma cadeia `Serial` com 3 passos:
  1. **SpawnNPC**: `EntityId = "Risen_Knight"`
  2. **DestroyBlock** (destrói o bloco usado como “âncora”)
  3. **ModifyInventory**: `AdjustHeldItemQuantity = -1` (consome 1 unidade do item na mão)

Depois disso, toca `ItemAnimationId: "CastHurlCharged"`.

Se falhar (não encontrou blocos do conjunto / não conseguiu aplicar):

- Executa `Simple` com `ItemAnimationId: "Interact"`

#### 3.4) Quais blocos contam como “Necromancy_Bones”

Arquivo do conjunto:

- `Server/Item/Block/Sets/Necromancy_Bones.json`

Conteúdo:

- `Deco_Bone_Pile`
- `Deco_Bone_Skulls`

Ou seja: **só funciona** perto/ao mirar em **pilhas de ossos** e **caveiras** (de decoração).

### 4) O NPC invocado: `Risen_Knight`

Definição:

- `Server/NPC/Roles/Undead/Skeleton/Risen_Knight.json`

O NPC é um **Variant** do template `Template_Summoned_Ally`, com ajustes:

- **Vida**: `MaxHealth = 74`
- **Aparência**: `Skeleton_Knight`
- **Armas**:
  - `Weapon_Sword_Steel_Rusty`
  - `Weapon_Shield_Rusty` (off-hand)
- **Comportamento de combate**:
  - `Attack = Root_NPC_Skeleton_Knight_Attack`
  - `AttackDistance = 3`
  - `DesiredAttackDistanceRange = [2, 2.5]`
- **FlockArray**: `[ "Risen_Knight" ]`
- **Nome**: `NameTranslationKey = server.npcRoles.Skeleton_Knight.name`

Em outras palavras: o item está “programado” para invocar um **cavaleiro esqueleto aliado** já com espada/escudo e IA de combate.

#### 4.1) Por que ele “segue o player que invocou”

Isso não aparece diretamente no `Risen_Knight.json` porque vem do template base:

- `Server/NPC/Roles/_Core/Templates/Template_Summoned_Ally.json`

Nesse template, o NPC:

- **Entra em um “flock” com um player** quando ainda não é membro (`JoinFlock`, `ForceJoin: true`, procurando player em `Range: 20`).
- Quando está **Idle** e é **Follower**, executa a instrução **“Follow the leader”** com `BodyMotion: Seek` (ele tenta ficar perto do líder; `SlowDownDistance: 7`, `StopDistance: 4`).

Na prática, como o `Risen_Knight` nasce perto de quem invocou, o player invocador acaba virando o “líder” do flock e ele passa a seguir esse player.

### 5) Som e animações usados (como o jogo “faz” a apresentação)

#### 5.1) Som de carregamento

ID usado pelo item:

- `SFX_Skeleton_Mage_Spellbook_Charge`

Arquivo:

- `Server/Audio/SoundEvents/SFX/Weapons/Spellbook/SFX_Skeleton_Mage_Spellbook_Charge.json`

Esse evento toca **múltiplas camadas** (folhas + magia + whoosh) com randomização de pitch/volume e `PreventSoundInterruption: true`.

#### 5.2) “Sound set” do item (drop/drag)

ID:

- `ISS_Weapons_Books`

Arquivo:

- `Server/Audio/ItemSounds/ISS_Weapons_Books.json`

Mapeia pelo menos:

- `Drop -> SFX_Drop_Weapons_Books`
- `Drag -> SFX_Drag_Weapons_Books`

#### 5.3) Animações de item

ID de animação usados:

- `CastHurlCharging`
- `CastHurlCharged`

Arquivo de mapeamento:

- `Server/Item/Animations/Spellbook.json`

Ele aponta para `.blockyanim` (1ª pessoa / 3ª pessoa / moving) em:

- `Characters/Animations/Items/Dual_Handed/Spellbook/...`

O item também define animação de drop:

- `Common/Items/Animations/Dropped/Dropped_Diagonal_Left.blockyanim`

### 6) Modelo/ícone (por que o item “parece” um grimório específico)

No item:

- `Model`: `NPC/Void/Necromancer_Void/Models/Weapons/Spellbook/Demon.blockymodel`
- `Texture`: `NPC/Void/Necromancer_Void/Models/Weapons/Spellbook/Demon_Texture.png`
- `Icon`: `Icons/ItemsGenerated/Weapon_Spellbook_Demon.png`

Arquivos correspondentes (no zip, com prefixo `Common/`):

- `Common/NPC/Void/Necromancer_Void/Models/Weapons/Spellbook/Demon.blockymodel`
- `Common/NPC/Void/Necromancer_Void/Models/Weapons/Spellbook/Demon_Texture.png`
- `Common/Icons/ItemsGenerated/Weapon_Spellbook_Demon.png`

### 7) Observações importantes / limitações da análise

- **Tradução do nome/descrição do item**: o item referencia as chaves
  - `server.items.Weapon_Spellbook_Rekindle_Embers.name`
  - `server.items.Weapon_Spellbook_Rekindle_Embers.description`
  
  Porém, **essas chaves não aparecem em nenhum `.lang` dentro do `Assets.zip`** que eu analisei.  
  Isso normalmente indica que:
  - a tradução pode estar em outro pacote (cliente/loc separado), ou
  - existe fallback/geração em runtime, ou
  - essa build está incompleta do lado de strings.

- **Semântica exata do `RunOnBlockTypes`**: a intenção é clara (scan de blocos num raio e aplica a cadeia), mas como esse tipo de interação aparece praticamente só aqui, detalhes como “ordem de seleção” (mais próximo vs aleatório vs mira) ficam implícitos no engine.

### 8) Checklist “funcionando” (como testar no jogo)

- Pegue o item `Weapon_Spellbook_Rekindle_Embers` (stack até 5)
- Encontre `Deco_Bone_Pile` ou `Deco_Bone_Skulls`
- Segure Primary/Secondary por **~0.8s**
- Resultado esperado:
  - toca som de charge + animação
  - aparece um `Risen_Knight`
  - o bloco de ossos é destruído
  - seu stack do item diminui em 1

### 9) Glossário (como o jogo modela a mecânica por JSON)

Esta seção complementa o relatório explicando os “blocos” de mecânica que aparecem nos JSONs.

#### 9.1) `Interactions.Primary` / `Interactions.Secondary` (Inputs)

No JSON de item, `Interactions` normalmente separa o que acontece ao usar o item com:

- **Primary**: input primário (normalmente clique/ataque principal)
- **Secondary**: input secundário (normalmente clique/uso alternativo)

No caso do `Weapon_Spellbook_Rekindle_Embers`, **Primary e Secondary são idênticos** (mesma mecânica em ambos os botões).

Exemplo (note como o bloco se repete):

```39:174:C:\Users\mateu\Downloads\HorseFollow\libs\Analise\analises\Rekindle_Embers\Server\Item\Items\Weapon\Spellbook\Weapon_Spellbook_Rekindle_Embers.json
  "Interactions": {
    "Primary": { "Interactions": [ { "Type": "Charging", ... } ] },
    "Secondary": { "Interactions": [ { "Type": "Charging", ... } ] }
  }
```

#### 9.2) `Charging` (carregar/segurar)

`Charging` é um tipo de interação que **fica ativo enquanto o input está sendo segurado** e “desvia” para próximos passos conforme o tempo/estado de carga.

Você vê isso tanto no seu item quanto em consumíveis do jogo. Exemplo didático (teste):

```1:13:C:\Users\mateu\Downloads\HorseFollow\libs\Analise\analises\Rekindle_Embers\Server\Item\Interactions\Tests\Charging.json
{
  "Type": "Charging",
  "Next": {
    "0.8": { "Type": "Simple", "RunTime": 0.5 },
    "1.8": { "Type": "Simple", "RunTime": 0.5 }
  }
}
```

No `Weapon_Spellbook_Rekindle_Embers`, o `Charging` usa um `Next` com chaves **`"0"`** e **`"0.8"`**:

- `"0"`: comportamento de “soltou cedo” (não faz nada relevante)
- `"0.8"`: comportamento de “carregou o suficiente” (aí roda `RunOnBlockTypes` e faz a invocação)

Além disso, existem flags que modulam o comportamento do charge, por exemplo:

- **`AllowIndefiniteHold`**: permite segurar sem “estourar”
- **`HorizontalSpeedMultiplier`**: desacelera o jogador enquanto segura
- **`CancelOnItemChange`**: cancela se trocar o item da mão

#### 9.3) `ModifyInventory` (consumo/alteração de inventário)

`ModifyInventory` é o bloco que **altera o inventário** como consequência de uma interação.

No seu item, ele é usado para **consumir 1 unidade** do item da mão:

```1:4:C:\Users\mateu\Downloads\HorseFollow\libs\Analise\analises\Rekindle_Embers\Server\Item\Interactions\Tests\ModifyInventory.json
{
  "Type": "ModifyInventory",
  "AdjustHeldItemQuantity": -1
}
```

O `Weapon_Spellbook_Rekindle_Embers` usa exatamente esse padrão dentro de uma cadeia `Serial` (depois de spawnar o NPC e destruir o bloco):

- `SpawnNPC` → `DestroyBlock` → `ModifyInventory (AdjustHeldItemQuantity: -1)`

Um exemplo maior (consumo “carregado”, típico de comida/potion) também usa `ModifyInventory` no final do charge:

```1:62:C:\Users\mateu\Downloads\HorseFollow\libs\Analise\analises\Rekindle_Embers\Server\Item\Interactions\Consumables\Consume_Charge.json
{
  "Type": "Serial",
  "Interactions": [
    {
      "Type": "Charging",
      "Next": {
        "4.0": {
          "Type": "ModifyInventory",
          "AdjustHeldItemQuantity": -1,
          "Next": { "Type": "Serial", "Interactions": [ /* efeitos */ ] }
        }
      }
    }
  ]
}
```

