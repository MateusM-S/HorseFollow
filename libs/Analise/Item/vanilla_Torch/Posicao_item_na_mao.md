# Posição do item na mão — onde é definida

Nos JSONs do Hytale **não existe** um campo tipo `PositionOffset` ou `RotationOffset` para o **item segurado na mão**. O que existe é o seguinte.

## BlockBoundingBoxes / HitboxType — não é posição na mão

Os arquivos em **Server/Item/Block/Hitboxes/** (ex.: `Furniture/Torch/Torch.json`) com **Boxes** e **Min/Max** (X, Y, Z) são **caixas de colisão do bloco quando colocado no mundo**. O item usa **HitboxType** (ex.: `"Torch"`) no **BlockType** para referenciar esse hitbox. Ou seja: definem onde o **bloco** ocupa espaço (para o jogador não atravessar a tocha colocada), **não** como o item fica na mão quando segurado. Para itens que não são colocados como bloco (ex.: Horn), isso não se aplica.

## 1. No JSON do item

- **Scale** (na raiz do item) — controla o **tamanho** do modelo quando segurado.  
  Ex.: `Furniture_Crude_Torch` tem `"Scale": 1.6`. Se não definir, o padrão é 1.
- **IconProperties** (Scale, Rotation, Translation) — vale só para o **ícone** no inventário, não para o item na mão.

Os `PositionOffset` / `RotationOffset` que aparecem nos itens da tocha estão dentro de **BlockType.Particles** (posição da chama, etc.), não do item segurado.

## 2. Onde a posição/rotação na mão vem de fato

A posição e a pose do item **na mão** são definidas pela **animação** (ex.: **Item/Idle**, **Item/Crouch**, Torch/Idle, Torch/Crouch). O conjunto de animações (PlayerAnimationsId: "Item", "Torch", "Horn", etc.) e o **estado** atual (Idle, Crouch, Walk, etc.) escolhem qual `.blockyanim` roda; é essa animação que define **como** o item fica na mão (osso, pose, orientação).

1. **Animação** (`.blockyanim`)  
   A animação do conjunto + estado (ex.: **Item/Crouch**, Item/Idle, Off_Handed/Torch/Idle) define em qual osso o item é anexado e a pose da mão/braço. Ou seja: **é a animação que define a posição do item na mão.**

2. **Pivot do modelo** (`.blockymodel`)  
   O pivot no editor (ex.: Blockbench) define o “ponto de pega” em relação ao osso: o engine cola o modelo no osso da animação e usa o pivot como referência. Ajustar o pivot afina como o item se alinha à mão.

Resumo: **posição/pose na mão = animação (ex. Item/Idle, Item/Crouch)** + pivot do modelo. Não há offset de posição/rotação no JSON do item para o item segurado.

## 3. No Horn

- Você já ajustou o pivot no modelo para a mão — isso é o principal.
- No JSON do item, o único controle extra é **Scale** (tamanho quando segurado). O Horn pode ter um `Scale` na raiz se quiser mudar o tamanho em relação ao padrão (1).
