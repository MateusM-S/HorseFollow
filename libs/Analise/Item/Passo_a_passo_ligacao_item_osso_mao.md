# Passo a passo: ligação do item ao osso da mão (Hytale)

Como o engine anexa o **modelo do item** ao **osso da mão** do personagem e como ajustar pose e posição.

---

## 1. Quem faz a “ligação”

- O **motor do jogo** faz a ligação: ele pega o **modelo do item** (`.blockymodel`) e anexa à **transformação** do osso da mão do personagem (ex.: **R-Hand** ou **L-Hand**).
- **Não** existe um campo no JSON do item tipo “attach to bone”; o osso é definido pelo **conjunto de animações** (Item = R-Hand, Torch = L-Hand) e pela animação do estado (Idle, Walk, etc.).

---

## 2. Passo a passo (resumo)

| # | O quê | Onde |
|---|--------|------|
| 1 | Definir **qual mão** | Conjunto de animações (Item = direita, Torch = esquerda). |
| 2 | Definir **pose do braço/mão** | Animação `.blockyanim` (Idle, Crouch, etc.) que anima R-Arm, R-Forearm, R-Hand (ou L-*). |
| 3 | Ajustar **onde o modelo “gruda” na mão** | **Nó raiz** do `.blockymodel` do item (position + orientation). |
| 4 | Ajustar **tamanho** | Campo `Scale` no JSON do item. |

---

## 3. Passo 1 — Escolher a mão (conjunto de animações)

- **Mão direita:** use um conjunto que herde ou use animações do **Item** (ex.: `PlayerAnimationsId: "Horn"` com `Parent: "Item"` em `Server/Item/Animations/Horn.json`). Essas animações mexem em **R-Arm**, **R-Forearm**, **R-Hand**.
- **Mão esquerda:** use um conjunto no estilo **Torch**, com animações que mexem em **L-Arm**, **L-Forearm**, **L-Hand** (ex.: `Characters/Animations/Items/Off_Handed/Torch/...`).

O engine usa o **conjunto + slot** (hotbar/utility) para decidir em qual mão o item aparece; o osso de anexação é o da mão animada (R-Hand ou L-Hand).

---

## 4. Passo 2 — Pose do braço/mão (animação)

- A **pose** (orientação do braço e da mão) vem da **animação** do estado atual: Idle, Walk, Crouch, etc.
- Cada animação é um `.blockyanim` com **nodeAnimations** para os ossos do **personagem** (não do modelo do item):
  - **Item (mão direita):** R-Arm, R-Forearm, R-Hand.
  - **Torch (mão esquerda):** L-Arm, L-Forearm, L-Hand.
- Em cada osso você define **orientation** (quaternion) por keyframe; **position** costuma ficar vazio.
- Para se basear na pose “Item/Idle”:
  - Use a referência em `libs/Analise/Item/vanilla_Item/Common/Characters/Animations/Items/Item/Idle.blockyanim`.
  - Ou extraia o Idle vanilla do Assets.zip com `ListAndExtractItemIdle.ps1` e edite no Blockbench se precisar.

**No Blockbench (animação do personagem):**

1. Abra o **rig do personagem** (esqueleto Hytale).
2. Abra/importe a animação **Idle** (Item ou Torch).
3. Os ossos animados são os da **mão que segura o item** (R-Arm, R-Forearm, R-Hand ou L-*).
4. Ajuste rotação (e raramente posição) desses ossos para mudar a **pose** do braço e da mão; o item seguirá a mão.

---

## 5. Passo 3 — Onde o modelo “gruda” na mão (pivot do item)

- O engine anexa o **modelo do item** ao osso da mão. O ponto de ancoragem é a **raiz do modelo** (primeiro nó do `.blockymodel`).
- A **position** e **orientation** desse **nó raiz** funcionam como **offset** em relação ao osso da mão:
  - **position:** desloca o item em relação à mão (ex.: para frente, para cima).
  - **orientation:** rotaciona o item em relação à mão.

**No Blockbench (modelo do item):**

1. Abra o **modelo do item** (ex.: `Horn.blockymodel`).
2. Selecione o **nó raiz** (ex.: L-Horn2 no Horn). Esse nó é o “pivot” na mão.
3. **Mover** o nó raiz (position): o item inteiro se desloca em relação à mão; use para encaixar na palma ou no punho.
4. **Rotacionar** o nó raiz (orientation): o item gira em relação à mão; use para alinhar o objeto ao punho/dedos.
5. Exporte de novo o `.blockymodel`. Não é necessário animar o modelo do item para “grudar”; a ligação é sempre na raiz.

**Dica:** Se o item aparecer deslocado ou torto na mão, ajuste **só a raiz** do modelo (position + orientation); não precisa mudar animação do personagem para esse ajuste fino.

---

## 6. Passo 4 — Tamanho na mão

- No **JSON do item** (ex.: `Server/Item/Items/Horn.json`), use o campo **Scale** (na raiz) para aumentar ou diminuir o tamanho do modelo quando segurado. Não afeta o pivot; só a escala.

---

## 7. Ordem prática recomendada

1. **Definir conjunto** (Item ou Torch) no `Server/Item/Animations/<SeuItem>.json` e no item JSON (`PlayerAnimationsId`).
2. **Usar ou editar a animação Idle** (e outras) para a pose do braço/mão que você quer.
3. **Ajustar o modelo do item:** abrir o `.blockymodel`, mexer na **raiz** (position e orientation) até o item ficar bem posicionado na mão.
4. **Ajustar Scale** no item JSON se precisar.

---

## 8. Referências no projeto

| O quê | Caminho |
|-------|--------|
| Animação Item/Idle (referência) | `libs/Analise/Item/vanilla_Item/Common/Characters/Animations/Items/Item/Idle.blockyanim` |
| Animação Tocha Idle (L-Arm, L-Hand) | `libs/Analise/Item/vanilla_Torch/Common/Characters/Animations/Items/Off_Handed/Torch/Idle.blockyanim` |
| Modelo Horn (raiz = pivot na mão) | `src/main/resources/Common/Items/Horn/Horn.blockymodel` |
| Visão geral “item na mão” | `libs/Analise/Item/Como_item_fica_na_mao_do_player.md` |

---

## 9. Resumo da “ligação com o osso”

- **Ligação** = engine anexa o modelo do item ao osso **R-Hand** ou **L-Hand** conforme o conjunto de animações.
- **Pose da mão** = definida pela animação (`.blockyanim`) do personagem (Idle, Walk, etc.).
- **Onde gruda** = definido pela **position** e **orientation** do **nó raiz** do modelo do item (`.blockymodel`).
- Não existe campo “bone attachment” no JSON do item; a ligação é implícita: conjunto → ossos animados → osso da mão → modelo anexado na raiz.
