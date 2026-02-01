# Furniture_Crude_Torch — como fica na mesma mão

## Conclusão

A tocha **não** tem duas animações (uma por mão). Ela usa **um único conjunto**: **Torch**, e **todas** as animações apontam para **Off_Handed/Torch** (pasta `Common/Characters/Animations/Items/Off_Handed/Torch/`). O engine usa sempre esse mesmo conjunto, tanto na barra de utilidades quanto na mão principal, por isso o item **permanece na mesma mão** nos dois casos.

## Item: Furniture_Crude_Torch.json

- **PlayerAnimationsId**: `"Torch"`
- **InteractionConfig.Priorities.Secondary**:
  - `MainHand: 1`  — prioridade maior para Secondary na mão principal
  - `OffHand: -1`  — prioridade menor na offhand
- **Utility.Usable**: `true`

## Conjunto: Server/Item/Animations/Torch.json

- **Sem Parent** — conjunto standalone (não herda de "Item").
- Todas as entradas em **Animations** usam caminhos **Characters/Animations/Items/Off_Handed/Torch/...** (Idle, Walk, Run, Interact, etc.).
- Ou seja: um único conjunto de poses, pensado para o slot de utilidade (off hand), e o jogo reutiliza esse mesmo conjunto nos dois slots para o item ficar sempre na mesma mão.

## Para o Horn

Para o Horn ficar “na mesma mão” nos dois contextos:

1. **Usar Parent "Torch"** — herda o comportamento da tocha (mesma mão). No Horn já tivemos problema na mão principal (mão esquerda subia). Pode ser que falte só alinhar prioridades.
2. **Copiar InteractionConfig da tocha** no item Horn: `Secondary: { MainHand: 1, OffHand: -1 }`, para o engine tratar Secondary de forma consistente entre mão principal e barra de utilidades.

Arquivos extraídos em `libs/Analise/Item/vanilla_Torch/` (item + Server/Item/Animations/Torch.json + animações Common/...).
