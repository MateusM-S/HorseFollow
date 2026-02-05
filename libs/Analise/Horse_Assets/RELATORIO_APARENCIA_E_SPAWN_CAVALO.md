# Relatório: Aparência do cavalo, Mane (Hair) e spawn no Hytale

**Fonte:** Assets.zip do Hytale (pesquisa e extração apenas em `libs/Analise/Horse_Assets/`).  
**Nada foi extraído na raiz do projeto.**

---

## 1. Como o jogo controla a “skin” do cavalo

### 1.1 Cadeia de referências

| O quê | Onde no Assets | Conteúdo relevante |
|------|----------------|--------------------|
| **Role** | `Server/NPC/Roles/Creature/Livestock/Horse.json` | `"Appearance": "Horse"` → aponta para o asset de modelo abaixo |
| **Model (aparência)** | `Server/Models/Livestock/Horse.json` | Modelo base, **uma** textura do corpo, e **RandomAttachmentSets** para o slot de crina |
| **Grupo (spawn)** | `Server/NPC/Groups/Creature/Livestock/Horse.json` | `"IncludeRoles": ["Horse*"]` → todos os roles cujo nome começa com "Horse" |

O asset em `Server/Models/Livestock/Horse.json` é o que o jogo usa quando o Role diz `Appearance: "Horse"`. Nele:

- **Model:** modelo base → `NPC/Livestock/Horse/Models/Model.blockymodel`
- **Texture:** uma única textura do corpo → `NPC/Livestock/Horse/Models/Texture.png`
- **RandomAttachmentSets:** um único slot, chamado **"Hair"** (no JSON; no Blockbench pode aparecer como “Mane”):
  - **Grey:** mesmo modelo de crina, textura `Mane_Grey.png`
  - **Black:** mesmo modelo de crina, textura `Mane_Black.png`

Ou seja: **uma** textura de corpo, **um** modelo de crina (Mane), **duas** variantes de crina (Grey e Black). Não há campo `Weight` nesse JSON; a escolha entre Grey e Black é provavelmente 50/50.

Trecho relevante de `Server/Models/Livestock/Horse.json`:

```json
"RandomAttachmentSets": {
  "Hair": {
    "Grey": {
      "Model": "NPC/Livestock/Horse/Models/Attachments/Mane.blockymodel",
      "Texture": "NPC/Livestock/Horse/Models/Attachments/Mane_Grey.png"
    },
    "Black": {
      "Model": "NPC/Livestock/Horse/Models/Attachments/Mane.blockymodel",
      "Texture": "NPC/Livestock/Horse/Models/Attachments/Mane_Black.png"
    }
  }
}
```

O “Mane” do Blockbench é esse slot **Hair**: um ponto de attachment com várias opções (Grey, Black). O modelo da crina é sempre `Mane.blockymodel`; só a textura muda.

---

## 2. Como é definido o spawn

- **Grupo:** `Server/NPC/Groups/Creature/Livestock/Horse.json`  
  - `"IncludeRoles": ["Horse*"]`  
  - Ou seja: qualquer **Role** cujo **nome** case com `Horse*` (ex.: `Horse`, `Horse_Foal`) entra nesse grupo.

- **Spawn no mundo:** em arquivos como:
  - `Server/NPC/Spawn/World/Zone1/Spawns_Zone1_Plains_Animal.json`
  - `Server/NPC/Spawn/World/Zone4/Spawns_Zone4_Wastes_Animal.json`  
  Eles listam NPCs por **Id** e **Weight** (peso no sorteio). Exemplo (Zone1):
  - `"Id": "Horse"` → refere-se ao **grupo** “Horse”
  - `"Weight": 5`
  - `"Flock": "Group_Medium"`  
  O jogo usa o **Id** para resolver o **grupo** “Horse”; ao spawnar, escolhe um dos roles que pertencem ao grupo (quem casa com `Horse*`). Não há peso por role dentro do grupo nesses JSONs; a lógica interna do jogo é que decide (ex.: sorteio uniforme entre Horse e Horse_Foal, ou similar).

Conclusão: **spawn é por grupo** (“Horse”); o grupo inclui todos os roles cujo nome bate com `Horse*`. Qualquer role novo cujo nome comece com “Horse” (ex.: `Horse_MySkin`) será incluído automaticamente nesse grupo. **Nota:** um role chamado `Black_Horse` **não** casa com `Horse*` (o padrão é prefixo: “Horse*” = começa com “Horse”). Para incluí-lo, é preciso **sobrescrever** o grupo no mod e listar explicitamente: `"IncludeRoles": ["Horse*", "Black_Horse"]`.

### Onde é definida a taxa de spawn (Weight)

A **taxa de spawn** (frequência com que o grupo “Horse” aparece em relação a Cow, Pig, etc.) é definida nos **arquivos de spawn por zona/ambiente**, no **Assets do jogo**:

- Caminho (exemplos):  
  `Server/NPC/Spawn/World/Zone1/Spawns_Zone1_Plains_Animal.json`  
  `Server/NPC/Spawn/World/Zone4/Spawns_Zone4_Wastes_Animal.json`  
  (e outros em `Server/NPC/Spawn/World/...` por zona/bioma.)

- Em cada arquivo há um array **NPCs**: cada entrada tem **Id** (nome do **grupo**, ex. `"Horse"`) e **Weight** (peso no sorteio). Exemplo (Zone1 Plains):

  `"Id": "Horse", "Weight": 5` → o grupo Horse participa do spawn nessa zona com peso 5; Cow tem 10, Pig 10, etc.

- Para **alterar a taxa de spawn do Horse** (ou adicionar spawn em outra zona): colocar no **mod** um JSON nesse mesmo caminho (ex.: `src/main/resources/Server/NPC/Spawn/World/Zone1/Spawns_Zone1_Plains_Animal.json`) com os mesmos campos e o **Weight** desejado para `"Id": "Horse"` (e os outros NPCs que quiser manter).

- A **proporção entre os roles dentro do grupo** (Horse vs Horse_Foal vs Black_Horse): nos assets vanilla o grupo só tem `IncludeRoles`; **não há Weight por role** no JSON do grupo. A escolha entre os roles do grupo é provavelmente uniforme ou definida pelo motor. Para mudar a proporção seria preciso documentação ou código do jogo (ex.: se existir algo como `RoleWeights` no grupo em versões futuras).

---

## 3. Opções: nova entidade vs ajustar o arquivo

### Opção A – Nova entidade (recomendado para “uma textura + um Mane específico”)

**Ideia:** Criar um **novo** Role (ex.: `Horse_MySkin`) e um **novo** Model asset (ex.: `Horse_MySkin.json`) que usa só a sua textura e só uma opção de Hair (ex.: Black ou Grey).

**Vantagens:**

- Não altera o cavalo vanilla: Horse e Horse_Foal continuam como estão.
- Spawn: como o grupo é `IncludeRoles: ["Horse*"]`, um role chamado `Horse_MySkin` **já é incluído** no grupo; nos biomas/zones que spawnam “Horse”, esse novo cavalo pode aparecer junto com Horse e Horse_Foal (a escolha exata depende do motor).
- Você controla tudo num único lugar: um Model com uma textura + um único tipo de crina.
- HorseFollow e o resto do mod continuam funcionando: basta que o role seja tratado como “cavalo” onde for necessário (ex.: bind, amuleto, etc.), se você quiser que Horse_MySkin também seja vinculável.

**Passos resumidos:**

1. **Novo Model asset** (ex.: `src/main/resources/Server/Models/Livestock/Horse_MySkin.json`):
   - Copiar `Server/Models/Livestock/Horse.json` do Assets.
   - Trocar **Texture** para a sua textura (ex.: `NPC/Livestock/Horse/Models/Texture_MySkin.png` ou caminho no seu mod).
   - Em **RandomAttachmentSets.Hair**, deixar **apenas uma** opção (ex.: só `"Black"` ou só `"Grey"`), assim o “Mane” fica fixo nessa crista.

2. **Novo Role** (ex.: `src/main/resources/Server/NPC/Roles/Creature/Livestock/Horse_MySkin.json`):
   - Copiar o `Horse.json` do Role; em **Modify** colocar `"Appearance": "Horse_MySkin"` (e o que mais quiser igual ao Horse).
   - Nome do arquivo = id do role; como o nome começa com `Horse`, o grupo `Horse*` já o inclui para spawn.

3. **Arquivos no mod:** incluir a nova textura (e, se precisar, uma cópia do `Mane.blockymodel` / texturas de Mane) nos caminhos referenciados pelo novo Model.

4. **Spawn:** não é obrigatório alterar grupo nem spawn: o grupo `Horse*` já cobre `Horse_MySkin`. Se quiser que esse cavalo **não** spawne em lugar nenhum, aí sim seria preciso um grupo separado ou lógica de spawn que exclua esse role (depende do que o motor permitir).

---

### Opção B – Ajustar o arquivo (override do Horse)

**Ideia:** Colocar no mod um `Server/Models/Livestock/Horse.json` que **substitui** o do jogo: mesma estrutura, mas com **sua** textura e **uma** opção de Hair (o Mane específico).

**Efeito:**

- **Todos** os cavalos que usam `Appearance: "Horse"` (Horse e qualquer outro role que aponte para "Horse") passam a usar essa única textura e essa única crina no mundo inteiro.
- Spawn não muda: continua pelo mesmo grupo e mesmos spawns; só a aparência global do Horse muda.

**Quando faz sentido:** Quando você quer um “reskin” global do cavalo (um único visual para todo o jogo), e não uma variante a mais.

---

## 4. Resumo e recomendação

| Pergunta | Resposta |
|----------|----------|
| Quem define textura + Mane? | O asset em `Server/Models/Livestock/Horse.json` (Model/“Appearance” “Horse”). |
| Slot da crina no JSON? | **Hair** (RandomAttachmentSets.Hair). No Blockbench pode ser “Mane”. |
| Quantas variantes de crina no vanilla? | Duas: Grey e Black (mesmo modelo, texturas diferentes). |
| Aleatoriedade ao nascer? | Escolha entre as opções de Hair (e, se houver, variantes de textura) – no vanilla sem Weight explícito, provavelmente 50/50. |
| Como o spawn usa o Horse? | Spawn usa o **grupo** “Horse”; o grupo inclui todos os roles com nome `Horse*`. |

**Recomendação:** Para ter **uma textura nova + um único Mane (crista)** sem mudar o cavalo normal do jogo, o melhor é **criar uma nova entidade**: novo Model asset (ex.: `Horse_MySkin`) com sua textura e Hair com uma opção só, e novo Role (ex.: `Horse_MySkin`) com `Appearance: "Horse_MySkin"`. O spawn já considera qualquer role “Horse*”, então não é obrigatório mexer em grupo ou spawn; só incluir os arquivos no mod e, se quiser, garantir que HorseFollow (ou outros sistemas) tratem esse role como cavalo onde fizer sentido.

Todos os arquivos usados nesta análise estão apenas em `libs/Analise/Horse_Assets/`; nada foi extraído na pasta principal do projeto.
