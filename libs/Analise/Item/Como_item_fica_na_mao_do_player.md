# Como o item fica na mão do player — busca detalhada

Resumo do que define **posição, pose e comportamento** do item segurado no Hytale, com base em código, assets e documentação.

---

## 1. Quem define o quê (visão geral)

| O quê | Onde é definido | Papel |
|-------|------------------|--------|
| **Qual mão** (direita/esquerda) | Slot (hotbar = main hand, utility = offhand) + conjunto de animações | O conjunto **Torch** anima só **L-Arm, L-Forearm, L-Hand** → item na mão esquerda. O conjunto **Item** anima **R-Arm, R-Forearm, R-Hand** → item na mão direita. |
| **Pose do braço/mão** (orientação) | **Animação** (`.blockyanim`) do conjunto | A animação move os ossos do **esqueleto do jogador** (R-Arm, R-Forearm, R-Hand ou L-Arm, L-Forearm, L-Hand). São keyframes de **orientation** (quaternion) por osso. |
| **Onde o modelo do item “gruda” na mão** | **Raiz do modelo** (`.blockymodel`) | O engine anexa o **modelo do item** ao osso da mão (R-Hand ou L-Hand). A **posição e orientação do nó raiz** do `.blockymodel` funcionam como pivot/offset em relação a esse osso. |
| **Tamanho do item na mão** | **Item JSON** → `Scale` (raiz) | Escala o modelo quando segurado. |
| **Qual conjunto de animações** | **Item JSON** → `PlayerAnimationsId` | Ex.: "Item", "Torch", "Horn". Cada ID aponta para um JSON em Server/Item/Animations/ (ex.: Horn.json com Parent "Item"). |
| **Prioridade main/off hand** | **Item JSON** → `InteractionConfig.Priorities.Secondary` | MainHand / OffHand (ex.: 1 e -1) influencia qual mão é usada na interação. |

Não existe no item JSON campo tipo **PositionOffset** ou **RotationOffset** para o item **segurado**; isso vem da animação + pivot do modelo.

---

## 2. Animação (`.blockyanim`) — pose do braço/mão

- As animações de **item na mão** animam ossos do **esqueleto do personagem**, não do modelo do item.
- Nomes usados nos assets da tocha (Off_Handed/Torch): **L-Arm**, **L-Forearm**, **L-Hand** (mão esquerda).
- Na animação do Horn (HornUseAnimação): **R-Arm**, **R-Forearm**, **Chest**, **Head**, **L-Arm**, **L-Forearm** (direita para o chifre, esquerda acompanha).
- Estrutura típica em cada `.blockyanim`:
  - **nodeAnimations** → um entry por osso (ex.: `"L-Hand"`).
  - Cada osso tem **position** (array, muitas vezes vazio) e **orientation** (keyframes com **time** e **delta** em quaternion x,y,z,w).
- Ou seja: **a posição/pose do item na mão é a pose da mão do personagem**, definida por essa animação (Idle, Crouch, Walk, etc.). O engine não usa um “offset de item” no JSON; ele usa a transform do osso da mão.

Conjunto **Torch** → só anima L-Arm / L-Forearm / L-Hand → item sempre na **mesma mão (esquerda)**.  
Conjunto **Item** (e Horn com Parent "Item") → anima R-Arm / R-Forearm / R-Hand → item na **mão direita**.

---

## 3. Modelo do item (`.blockymodel`) — pivot / “onde gruda”

- O **modelo do item** (ex.: Horn.blockymodel) tem sua própria árvore de **nodes** (ex.: L-Horn2, L-Horn3, …). Esses nomes são do **modelo do chifre**, não do personagem.
- O engine anexa o **modelo inteiro** a um osso da mão do personagem (R-Hand ou L-Hand). O ponto de ancoragem é a **raiz do modelo** (primeiro nó, ex.: **L-Horn2** no Horn).
- A **position** e **orientation** desse nó raiz no `.blockymodel` definem o **offset em relação ao osso da mão**. Ajustar no Blockbench (mover/rotacionar a raiz) altera como o item “senta” na mão.
- Resumo: **posição na mão = transform do osso (da animação) + position/orientation da raiz do .blockymodel**.

---

## 4. Item JSON — o que influencia “na mão”

- **PlayerAnimationsId** (ex.: "Horn") → escolhe o conjunto (Server/Item/Animations/Horn.json). Esse conjunto define Idle, Crouch, Horn_Blow, etc., e pode ter **Parent: "Item"** para herdar Idle/Crouch/Walk do Item.
- **Scale** (raiz) → escala do modelo quando segurado (ex.: 1.6 na tocha, 0.8 no Horn).
- **InteractionConfig.Priorities.Secondary** → MainHand: 1, OffHand: -1 (como na tocha) para priorizar uma mão na interação.
- **IconProperties** → só para o ícone no inventário; não afeta o item na mão.
- **BlockBoundingBoxes / HitboxType** → só para **bloco colocado no mundo** (colisão), não para item segurado.

Nenhum campo do item JSON define **posição ou rotação** do item na mão; isso é **animação + pivot do modelo**.

---

## 5. Conjunto de animações (Server/Item/Animations/*.json)

- Ex.: **Horn.json** com **Parent: "Item"** → herda Idle, Walk, Crouch, Run, etc. do conjunto Item; só sobrescreve **Horn_Blow**.
- Cada entrada (Idle, Crouch, Horn_Blow, …) aponta para `.blockyanim` em **Common/Characters/Animations/Items/…** (ex.: Off_Handed/Torch/Idle.blockyanim ou Horn/HornUseAnimação.blockyanim).
- O **estado** do personagem (parado, andando, agachado, usando) escolhe qual animação rodar (Idle, Walk, Crouch, etc.). Assim, **quem “define” a posição do item na mão é a animação do estado atual** (Item/Idle, Item/Crouch, etc.).

---

## 6. Comparação Tocha vs Horn (na prática)

- **Tocha (Torch):** conjunto sem Parent; todas as animações em **Off_Handed/Torch/** e animam **L-Arm, L-Forearm, L-Hand**. O item fica sempre na **mesma mão (esquerda)** em hotbar e barra de utilidades.
- **Horn:** conjunto com **Parent: "Item"**. Idle/Crouch/Walk vêm do Item (animam R-Arm, R-Forearm, R-Hand). Só Horn_Blow é custom (HornUseAnimação). O chifre segue na **mão direita**; ajuste fino é no **pivot do Horn.blockymodel** (raiz L-Horn2).

---

## 7. Onde ajustar para “mudar como o item fica na mão”

1. **Pose do braço/mão (orientação)**  
   → Editar a **animação** do estado (ex.: Item/Idle ou Item/Crouch) no Blockbench e exportar de novo o `.blockyanim`, ou criar um conjunto próprio que use suas animações em vez de herdar do Item.

2. **Posição/rotação do modelo em relação à mão**  
   → Editar o **modelo do item** (Horn.blockymodel) no Blockbench: **posição e orientação do nó raiz** (ex.: L-Horn2). Isso é o “pivot” do item na mão.

3. **Tamanho**  
   → **Scale** no item JSON.

4. **Qual mão**  
   → Definido pelo **conjunto** (Torch = esquerda, Item = direita) e pelo **slot** (hotbar vs utility). Não há campo “hand” no item JSON; o engine usa o conjunto + slot.

---

## 8. Referências no projeto

- Animação Tocha Idle (L-Arm, L-Forearm, L-Hand):  
  `libs/Analise/Item/vanilla_Torch/Common/Characters/Animations/Items/Off_Handed/Torch/Idle.blockyanim`
- Animação Horn uso (R-Arm, R-Forearm, etc.):  
  `src/main/resources/Common/Characters/Animations/Items/Horn/HornUseAnimação.blockyanim`
- Modelo Horn (raiz = L-Horn2):  
  `src/main/resources/Common/Items/Horn/Horn.blockymodel`
- Conjunto Horn:  
  `src/main/resources/Server/Item/Animations/Horn.json`
- Item Horn:  
  `src/main/resources/Server/Item/Items/Horn.json`

Documento gerado a partir de busca em recursos do projeto, assets extraídos da tocha e documentação/webs sobre Hytale e Blockbench.
