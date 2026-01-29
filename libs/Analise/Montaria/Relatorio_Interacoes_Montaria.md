# Relatório Detalhado: Inputs, Interações com NPCs e Sistema de Montaria no Hytale

**Data:** 29 de Janeiro de 2026  
**Projeto:** HorseFollow  
**Foco:** Sistema de interação com NPCs, teclas de interação, e mecânica de montaria

---

## 1. Visão Geral do Sistema de Interação

O Hytale implementa interações com NPCs através de um sistema baseado em **Instructions** (instruções) que são avaliadas continuamente pelo motor do jogo. O sistema principal está localizado em `InteractionInstruction` dentro dos arquivos de role de NPC.

### 1.1) Estrutura Base: `InteractionInstruction`

O `InteractionInstruction` é um bloco de configuração que define **quando e como** um NPC pode ser interagido. Ele contém:

- **`Enabled`**: Condição que habilita/desabilita toda a instrução
- **`Instructions`**: Array de instruções individuais que são processadas sequencialmente

**Localização no código:**
- `src/main/resources/Server/NPC/Roles/_Core/Templates/Template_Animal_Tamed.json` (linhas 3068-3213)

**Exemplo de estrutura:**
```json
"InteractionInstruction": {
  "Enabled": {
    "Compute": "IsHarvestable || IsMountable"
  },
  "Instructions": [
    // Instruções para harvest
    // Instruções para montaria
  ]
}
```

---

## 2. Tecla de Interação (F Key)

### 2.1) Como Funciona

A tecla padrão de interação no Hytale é **F** (configurável pelo jogador nas opções). Quando o jogador pressiona F próximo a um NPC interagível:

1. O sistema verifica se o NPC tem `InteractionInstruction` habilitado
2. Verifica se as condições de `CanInteract` são satisfeitas
3. Se sim, mostra o prompt visual (texto "Press F to...")
4. Quando F é pressionado, dispara o sensor `HasInteracted`
5. A ação correspondente é executada

### 2.2) Controle de Visibilidade do Prompt

O prompt da tecla F é controlado pela ação `SetInteractable`:

**Parâmetros:**
- **`Interactable`**: `true`/`false` - Habilita ou desabilita a interação
- **`ShowPrompt`**: `true`/`false` - Controla se o prompt visual aparece (padrão: `true` se não especificado)
- **`Hint`**: String - Chave de tradução para o texto do prompt (ex: `"server.interactionHints.mount"`)

**Exemplo de desabilitar prompt mas manter interação:**
```json
{
  "Type": "SetInteractable",
  "Interactable": true,
  "ShowPrompt": false
}
```

**Exemplo de habilitar com prompt customizado:**
```json
{
  "Type": "SetInteractable",
  "Interactable": true,
  "Hint": "server.interactionHints.mount"
}
```

### 2.3) Como Ativar/Desativar Interação

#### Ativar Interação:
```json
{
  "Type": "SetInteractable",
  "Interactable": true
}
```

#### Desativar Interação:
```json
{
  "Type": "SetInteractable",
  "Interactable": false
}
```

#### Lógica Condicional (exemplo do Template_Animal_Tamed):

**Desativa se não pode interagir:**
```json
{
  "Sensor": {
    "Type": "Not",
    "Sensor": {
      "Type": "CanInteract",
      "ViewSector": 360,
      "Attitudes": ["Friendly", "Revered"]
    }
  },
  "Actions": [
    {
      "Type": "SetInteractable",
      "Interactable": false
    }
  ]
}
```

**Ativa se pode interagir:**
```json
{
  "Continue": true,
  "Sensor": {
    "Type": "Any"
  },
  "Actions": [
    {
      "Type": "SetInteractable",
      "Interactable": true,
      "Hint": "server.interactionHints.mount"
    }
  ]
}
```

---

## 3. Sensores de Interação

### 3.1) `CanInteract`

Verifica se o jogador **pode** interagir com o NPC baseado em:

- **`ViewSector`**: Ângulo de visão (em graus, 360 = qualquer direção)
- **`Attitudes`**: Array de atitudes permitidas (ex: `["Friendly", "Revered"]`)
- **Distância**: Implícita (o jogador precisa estar próximo)

**Exemplo:**
```json
{
  "Type": "CanInteract",
  "ViewSector": 360,
  "Attitudes": ["Friendly", "Revered"]
}
```

### 3.2) `HasInteracted`

Sensor que detecta quando o jogador **realmente interagiu** (pressionou F). Este é o gatilho para executar a ação de montaria.

**Exemplo:**
```json
{
  "Sensor": {
    "Type": "HasInteracted"
  },
  "Actions": [
    {
      "Type": "Mount",
      // ... configurações de montaria
    }
  ]
}
```

### 3.3) `InteractionContext`

Sensor que verifica se o jogador está segurando um item/tool específico que permite uma interação contextual (ex: tesoura para tosquiar).

**Exemplo (harvest com tool):**
```json
{
  "Type": "InteractionContext",
  "Context": {
    "Compute": "HarvestInteractionContext"
  }
}
```

---

## 4. Sistema de Montaria Completo

### 4.1) Propriedades de Montaria no Role

Para tornar um NPC montável, você precisa definir as seguintes propriedades no role:

#### `IsMountable`
- **Tipo**: Boolean
- **Descrição**: Define se o NPC pode ser montado
- **Valor padrão no template**: `false`
- **Localização**: Parâmetro do template ou campo direto no role

**Exemplo:**
```json
"IsMountable": true
```

#### `IsMemory`
- **Tipo**: Boolean
- **Descrição**: Define se o NPC é uma "memória" (persiste após logout/restart)
- **Valor padrão no template**: `false`
- **Relevância para montaria**: Geralmente montarias devem ser memórias para persistirem

**Exemplo:**
```json
"IsMemory": true
```

#### `MountAnchorX`, `MountAnchorY`, `MountAnchorZ`
- **Tipo**: Number (float)
- **Descrição**: Posição relativa onde o jogador será posicionado ao montar (em relação ao centro do NPC)
- **Unidade**: Blocos
- **Valores típicos para Horse**:
  - `MountAnchorX`: `0` (centro)
  - `MountAnchorY`: `1.6` (altura do dorso)
  - `MountAnchorZ`: `0` (centro) ou `0.2` (ligeiramente à frente)

**Exemplo (Horse_Friendly):**
```json
"MountAnchorX": 0,
"MountAnchorY": 1.6,
"MountAnchorZ": 0
```

**Exemplo (Ram_Friendly):**
```json
"MountAnchorX": 0,
"MountAnchorY": 1.3,
"MountAnchorZ": 0.2
```

#### `MountMovementConfig`
- **Tipo**: String (ID de configuração de movimento)
- **Descrição**: Define o comportamento de movimento quando montado
- **Valor padrão**: `"Mount"`
- **Localização**: Parâmetro do template

**Exemplo:**
```json
"MountMovementConfig": {
  "Value": "Mount"
}
```

### 4.2) Ação `Mount` (Component_Instruction_Interaction_Mount)

A ação `Mount` é executada quando o sensor `HasInteracted` é disparado. Ela é o equivalente ao `Component_Instruction_Interaction_Mount` mencionado.

**Estrutura completa:**
```json
{
  "Type": "Mount",
  "AnchorX": {
    "Compute": "MountAnchorX"
  },
  "AnchorY": {
    "Compute": "MountAnchorY"
  },
  "AnchorZ": {
    "Compute": "MountAnchorZ"
  },
  "MovementConfig": {
    "Compute": "MountMovementConfig"
  }
}
```

**Parâmetros:**
- **`AnchorX`, `AnchorY`, `AnchorZ`**: Posição de ancoragem (pode usar `Compute` para ler do parâmetro do role)
- **`MovementConfig`**: Configuração de movimento (pode usar `Compute` para ler do parâmetro)

### 4.3) Fluxo Completo de Montaria no Template_Animal_Tamed

**Localização:** `Template_Animal_Tamed.json` linhas 3150-3210

**Estrutura:**
```json
{
  "Enabled": {
    "Compute": "IsMountable"
  },
  "Instructions": [
    // 1. Desativa interação se não pode interagir
    {
      "Sensor": {
        "Type": "Not",
        "Sensor": {
          "Type": "CanInteract",
          "ViewSector": 360,
          "Attitudes": ["Friendly", "Revered"]
        }
      },
      "Actions": [
        {
          "Type": "SetInteractable",
          "Interactable": false
        }
      ]
    },
    // 2. Ativa interação se pode interagir
    {
      "Continue": true,
      "Sensor": {
        "Type": "Any"
      },
      "Actions": [
        {
          "Type": "SetInteractable",
          "Interactable": true,
          "Hint": "server.interactionHints.mount"
        }
      ]
    },
    // 3. Executa montaria quando F é pressionado
    {
      "Sensor": {
        "Type": "HasInteracted"
      },
      "Actions": [
        {
          "Type": "Mount",
          "AnchorX": { "Compute": "MountAnchorX" },
          "AnchorY": { "Compute": "MountAnchorY" },
          "AnchorZ": { "Compute": "MountAnchorZ" },
          "MovementConfig": { "Compute": "MountMovementConfig" }
        }
      ]
    }
  ]
}
```

### 4.4) Como Desmontar

O desmonte no Hytale é controlado pelo **sistema de input do jogador**, não pelo NPC. Normalmente é a tecla **Shift** (ou a tecla configurada para "dismount"). O NPC não precisa de configuração especial para permitir desmonte - isso é gerenciado pelo motor do jogo quando o jogador está montado.

**Nota:** O código do HorseFollow verifica `MountedComponent` para saber se o jogador está montado, mas o desmonte em si é gerenciado pelo jogo.

---

## 5. Appearance e Skeletons

### 5.1) `Appearance`

O campo `Appearance` define o modelo visual do NPC. Para cavalos, os valores comuns são:

- **`"Horse"`**: Modelo padrão de cavalo
- **`"Horse_Skeleton"`**: Modelo de esqueleto de cavalo (não encontrado no projeto atual, mas pode existir no jogo base)
- **`"Horse_Skeleton_Armored"`**: Modelo de esqueleto de cavalo com armadura (não encontrado no projeto atual)

**Exemplo:**
```json
"Appearance": "Horse"
```

**Localização nos roles:**
- `Horse.json`: `"Appearance": "Horse"`
- `Horse_Friendly.json`: `"Appearance": "Horse"`
- `Ram.json`: `"Appearance": "Ram"`
- `Ram_Friendly.json`: `"Appearance": "Ram"`

### 5.2) Relação com Montaria

O `Appearance` **não afeta diretamente** a capacidade de montaria. Um NPC com `Appearance: "Horse_Skeleton"` pode ser montável se tiver `IsMountable: true` e as configurações de montaria corretas. O `Appearance` apenas define como o NPC **parece visualmente**.

---

## 6. Test_Interaction_Follow

**Nota:** `Test_Interaction_Follow` **não foi encontrado** no código atual do projeto HorseFollow. Isso pode ser:

1. Uma referência a um sistema de teste interno do Hytale
2. Um componente que existe no jogo base mas não está sendo usado neste mod
3. Uma referência a documentação externa ou outro projeto

**Possível interpretação:** Pode ser um tipo de interação de teste que faz um NPC seguir o jogador, mas no HorseFollow isso é implementado via sistema de **Flock** (onde o jogador vira LEADER e o cavalo vira MEMBER), não via interação direta.

---

## 7. Exemplos Práticos no HorseFollow

### 7.1) Horse_Friendly.json

**Arquivo:** `src/main/resources/Server/NPC/Roles/Creature/Livestock/Horse_Friendly.json`

**Configurações de montaria:**
```json
{
  "IsMemory": true,
  "IsMountable": true,
  "MountAnchorX": 0,
  "MountAnchorY": 1.6,
  "MountAnchorZ": 0,
  "Appearance": "Horse"
}
```

**Comportamento:**
- Herda `InteractionInstruction` do `Template_Animal_Tamed`
- Como `IsMountable: true`, o template habilita a instrução de montaria
- O jogador pode pressionar F próximo ao cavalo (se atitude Friendly/Revered)
- Ao pressionar F, o cavalo é montado na posição (0, 1.6, 0) relativa ao centro

### 7.2) Integração com FollowService

O `FollowService` do HorseFollow **não interfere diretamente** no sistema de interação/montaria. Ele:

- Verifica se o jogador está montado via `MountedComponent`
- Remove `FlockMembership` quando montado (para evitar conflito de IA)
- Restaura `FlockMembership` quando desmontado
- Gerencia vínculos e teleportes, mas **não** controla a tecla F ou a ação de montar

---

## 8. Resumo de Campos e Valores

### 8.1) Campos de Montaria

| Campo | Tipo | Descrição | Exemplo |
|-------|------|-----------|---------|
| `IsMountable` | Boolean | NPC pode ser montado | `true` |
| `IsMemory` | Boolean | NPC persiste após logout | `true` |
| `MountAnchorX` | Float | Offset X da posição de montaria | `0` |
| `MountAnchorY` | Float | Offset Y (altura) da posição de montaria | `1.6` |
| `MountAnchorZ` | Float | Offset Z da posição de montaria | `0` ou `0.2` |
| `MountMovementConfig` | String | ID da config de movimento | `"Mount"` |

### 8.2) Ações de Interação

| Ação | Descrição | Parâmetros Principais |
|------|-----------|----------------------|
| `SetInteractable` | Habilita/desabilita interação | `Interactable`, `ShowPrompt`, `Hint` |
| `Mount` | Executa montaria | `AnchorX`, `AnchorY`, `AnchorZ`, `MovementConfig` |

### 8.3) Sensores de Interação

| Sensor | Descrição | Parâmetros Principais |
|--------|-----------|----------------------|
| `CanInteract` | Verifica se pode interagir | `ViewSector`, `Attitudes` |
| `HasInteracted` | Detecta quando F foi pressionado | Nenhum |
| `InteractionContext` | Verifica item/tool na mão | `Context` |

---

## 9. Como Customizar

### 9.1) Mudar a Posição de Montaria

Edite os valores de `MountAnchorX`, `MountAnchorY`, `MountAnchorZ` no role:

```json
"MountAnchorX": 0.5,    // Ligeiramente à direita
"MountAnchorY": 1.8,    // Mais alto
"MountAnchorZ": -0.3    // Ligeiramente atrás
```

### 9.2) Desabilitar Prompt mas Manter Interação

No `InteractionInstruction`, use `ShowPrompt: false`:

```json
{
  "Type": "SetInteractable",
  "Interactable": true,
  "ShowPrompt": false
}
```

### 9.3) Mudar Atitudes Necessárias para Montar

No sensor `CanInteract`, ajuste o array `Attitudes`:

```json
{
  "Type": "CanInteract",
  "ViewSector": 360,
  "Attitudes": ["Friendly", "Revered", "Neutral"]  // Adiciona Neutral
}
```

### 9.4) Customizar Texto do Prompt

1. Defina uma chave de tradução no `server.lang`:
   ```
   server.interactionHints.mount_custom=Montar Cavalo
   ```

2. Use no `SetInteractable`:
   ```json
   {
     "Type": "SetInteractable",
     "Interactable": true,
     "Hint": "server.interactionHints.mount_custom"
   }
   ```

---

## 10. Referências de Arquivos

### Arquivos Principais Analisados:

1. **Template de Montaria:**
   - `src/main/resources/Server/NPC/Roles/_Core/Templates/Template_Animal_Tamed.json`
   - Linhas 3068-3213: `InteractionInstruction` completo

2. **Roles de Montaria:**
   - `src/main/resources/Server/NPC/Roles/Creature/Livestock/Horse_Friendly.json`
   - `src/main/resources/Server/NPC/Roles/Creature/Livestock/Horse.json`
   - `src/main/resources/Server/NPC/Roles/Creature/Livestock/Ram_Friendly.json`
   - `src/main/resources/Server/NPC/Roles/Creature/Livestock/Ram.json`

3. **Código Java (referências):**
   - `src/main/java/com/horsefollow/FollowService.java` - Gerencia vínculos, não interações
   - `src/main/java/com/horsefollow/commands/HorseFollowCommand.java` - Usa `MountedComponent` para verificar montaria

---

## 11. Conclusão

O sistema de interação e montaria no Hytale é baseado em:

1. **`InteractionInstruction`** - Define quando e como interagir
2. **Sensores** (`CanInteract`, `HasInteracted`) - Detectam condições e ações do jogador
3. **Ações** (`SetInteractable`, `Mount`) - Executam o comportamento
4. **Propriedades do Role** (`IsMountable`, `MountAnchor*`) - Configuram a montaria

A tecla **F** é gerenciada pelo motor do jogo, e o NPC apenas responde a ela através do sensor `HasInteracted`. O prompt visual é controlado por `ShowPrompt` na ação `SetInteractable`.

Para desmontar, o jogador usa a tecla configurada (geralmente **Shift**), que é gerenciada pelo jogo, não pelo NPC.

---

**Fim do Relatório**
