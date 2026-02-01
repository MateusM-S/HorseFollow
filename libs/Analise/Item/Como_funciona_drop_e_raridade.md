# Como funciona o drop e a raridade (Hytale)

Resumo do vínculo **NPC → lista de drop** e de onde vem a **chance** e a **raridade** dos itens.

---

## 1. Vínculo NPC → lista de drop

- O **NPC** (ex.: **Praetorian Skeleton**, ID `SKELETON_BURNT_PRAETORIAN`) tem no seu role um campo:
  - **`DropList`**: valor = **ID da lista de drop** (ex.: `"Drop_Skeleton_Burnt_Praetorian"`).
- Quando o NPC **morre**, o jogo usa esse ID para carregar a **lista de drop** correspondente e decide o que dropar com base nas entradas dessa lista (chance/peso).

Ou seja: **quem droppa** é o NPC; **o que e com que chance** está na lista cujo ID está em `DropList`.

---

## 2. Onde fica a lista de drop (Drop_Skeleton_Burnt_Praetorian)

- A lista **Drop_Skeleton_Burnt_Praetorian** é um **recurso separado** do role do NPC.
- No Assets do Hytale ela fica em algum JSON de **drop lists** (ex.: `Server/NPC/Drop/`, `Server/Drop/`, ou pasta equivalente). O **nome do arquivo** ou o **Id** dentro do JSON costuma ser o ID da lista (`Drop_Skeleton_Burnt_Praetorian`).
- Para achar o arquivo: no Assets.zip, procure por `Drop_Skeleton_Burnt_Praetorian` em arquivos JSON ou por arquivos cujo nome/caminho lembre “drop” / “skeleton” / “praetorian”.

---

## 3. Como a “raridade” é definida (dois lugares)

### 3.1 Raridade do item (aparência / qualidade)

- Definida no **JSON do item** (ex.: `Server/Item/Items/.../Weapon_Spellbook_Rekindle_Embers.json`).
- Campo típico: **`Quality`** (ex.: `"Common"`, `"Uncommon"`, `"Rare"`, `"Epic"`).
- Isso controla **como o item aparece** (cor, texto “raro”, etc.). **Não** define a chance de drop; só a classificação visual do item.

### 3.2 Chance de dropar (probabilidade)

- Definida na **lista de drop** (ex.: o JSON de **Drop_Skeleton_Burnt_Praetorian**).
- Essa lista tem **entradas**: cada entrada associa um **item** (ex.: `ItemId`) a uma **probabilidade**.
- O jogo usa um destes esquemas (o que estiver no JSON da lista):
  - **Chance**: valor entre 0 e 1 (ou 0–100%) — “rola um número; se for menor que Chance, dropa”.
  - **Weight** (peso): várias entradas com peso; o jogo sorteia qual entrada ganha (proporcional ao peso). Itens “mais raros” têm peso menor.

Ou seja: **raridade visual** = campo do item (`Quality`); **raridade na prática** (ser raro de dropar) = **chance ou peso** da entrada na **lista de drop**.

---

## 4. Resumo prático (Praetorian Skeleton)

| O quê | Onde |
|-------|------|
| NPC | Role com ID `SKELETON_BURNT_PRAETORIAN`; campo `DropList`: `"Drop_Skeleton_Burnt_Praetorian"`. |
| Lista de drop | Recurso com ID `Drop_Skeleton_Burnt_Praetorian` (JSON em Server/NPC ou Drop no Assets). |
| O que droppa e quantidade | Entradas dessa lista (ItemId + Chance ou Weight, etc.). |
| Raridade “visual” do item | Campo `Quality` no JSON do **item**. |
| “Raridade” de drop (ser difícil de cair) | **Chance** menor ou **Weight** menor na **entrada** dessa lista. |

Para entender **exatamente** como a raridade do spellbook (ou de qualquer item) está configurada para o Praetorian Skeleton, abra no Assets o JSON da lista **Drop_Skeleton_Burnt_Praetorian** e veja as entradas (ItemId, Chance, Weight, ou o campo que o jogo usar para probabilidade).
