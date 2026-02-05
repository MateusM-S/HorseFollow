# Como é feita a troca de textura/ícone (vazio vs cheio) — caixa de captura e colar

**Pergunta:** Como a caixa de captura mostra ícone/textura diferente quando está vazia e quando está cheia? Como fazer o mesmo para o colar (vazio vs com alma)?

---

## 1. O que temos no projeto

- No **HytaleServer.jar** existem **UseCaptureCrateInteraction** e **CapturedNPCMetadata** (metadados do NPC capturado no item).
- Nos **itens do HorseFollow** (Horn, Horse_Feed, Ram_Feed) cada item tem um único **Icon** e **Texture** no JSON; não há exemplo de “mesmo item, dois visuais”.
- Na documentação de **Item Properties** (HytaleDocs), o campo **icon** é uma string (caminho da textura do ícone no inventário); não aparece “icon por metadata” ou “override por estado”.
- No JAR há **ItemStack$Metadata** e **item/config/metadata/AdventureMetadata**; ou seja, item pode ter metadata, mas não encontramos classe tipo **ItemIconOverride** ou **IconByMetadata** que mude o ícone conforme a metadata.

**Conclusão:** Não temos o JSON da Capture Crate no projeto, então não dá para afirmar **exatamente** como o jogo troca o visual vazio/cheio. As opções plausíveis são as duas abaixo.

---

## 2. Duas formas de ter “vazio” e “cheio”

### Opção A — Dois itens com IDs diferentes (recomendado para o colar)

- **Colar vazio:** um item (ex.: `Collar_Skeleton_Empty`) com seu **Icon** e **Texture** (e blockmodel) próprios.
- **Colar com alma:** outro item (ex.: `Collar_Skeleton_Soul`) com **Icon** e **Texture** (e blockmodel) diferentes — por exemplo colar brilhante/colorido.

**Fluxo:** Ao usar o colar vazio no cadáver do cavalo esqueleto, a interação **remove** 1 `Collar_Skeleton_Empty` e **dá** 1 `Collar_Skeleton_Soul` (com metadata guardando o UUID do cavalo). Cada item tem seu próprio JSON, então cada um tem seu próprio ícone e textura. Não depende de o cliente “ler metadata para trocar ícone”.

**Vantagem:** Funciona só com JSON + interação (e um pouco de plugin se precisar guardar UUID na metadata). Você cria dois blockmodels e duas texturas (vazio e cheia) e associa cada um a um item.

---

### Opção B — Um só item com metadata (como pode ser a caixa)

- Um único ID de item (ex.: Capture Crate) que pode ter **metadata** (ex.: **CapturedNPCMetadata**).
- O **cliente** do Hytale, ao desenhar o ícone do item, verifica se a stack tem esse tipo de metadata; se tiver, usa outro ícone (ou modelo) definido no asset do item — por exemplo um campo tipo “IconWhenCaptured” ou “ModelOverride” quando há CapturedNPCMetadata.

Nesse caso a “troca” seria: **mesmo item**, **mesma ID**, **metadata presente ou não** → engine escolhe ícone/textura conforme a metadata. Isso exigiria suporte no cliente/assets do jogo; não encontramos isso documentado nem no JAR do servidor (só a existência de metadata no item).

**Para descobrir se a caixa usa isso:** seria preciso abrir o **Assets.zip** do jogo, achar o item da Capture Crate (ex.: `Server/Item/Items/.../Capture_Crate.json` ou nome parecido) e ver se há algo como:
- dois **Icon** (um “vazio”, um “cheio”), ou  
- **IconOverride** / **ModelOverride** condicionado a metadata.

Se existir, você poderia tentar algo parecido para o colar (um item só + metadata “alma” + override no JSON). Como não temos esse JSON aqui, a opção **A** é a mais segura.

---

## 3. Recomendações para o colar

1. **Fazer dois itens (vazio e com alma)**  
   - `Collar_Skeleton_Empty`: ícone e textura “vazio”; usado no cadáver para capturar a alma.  
   - `Collar_Skeleton_Soul`: ícone e textura “cheio” (cor/ID diferente); guarda o UUID no item (metadata ou dado do mod); usado para invocar o cavalo.  
   Assim a “troca de textura” é na verdade **troca de item**: cada um com seu **Icon** e **Texture** (e blockmodel) no JSON.

2. **Blockmodel e textura**  
   Você disse que vai criar o blockmodel e a textura. Crie:
   - um conjunto (modelo + textura) para o **colar vazio**;
   - outro para o **colar com alma** (por exemplo mesma base com brilho/cor diferente).  
   Cada conjunto é referenciado no JSON do item correspondente (**Model**, **Texture**, **Icon**).

3. **Se no futuro você tiver o JSON da Capture Crate**  
   Vale extrair para `libs/Analise/` e ver se eles usam um item só + metadata + override de ícone; se usarem, dá para avaliar replicar para o colar com um item só.

Resumindo: **a troca “vazio/cheio” no colar pode ser feita com dois itens e dois visuais (Icon/Texture/Model)**, sem depender de override por metadata. Assim você controla exatamente o que aparece em cada estado.
