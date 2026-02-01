# Furniture_Crude_Torch — referência para Horn “mesma mão”

Os assets da tocha já foram extraídos para `libs/Analise/Item/vanilla_Torch/` (script `ListTorchInAssets.ps1`).

## Descoberta

A tocha **não** tem duas animações (uma por mão). Ela usa **um único conjunto** `Torch`, com **todas** as animações em `Common/Characters/Animations/Items/Off_Handed/Torch/`. O engine usa esse mesmo conjunto na barra de utilidades e na mão principal, por isso o item **permanece na mesma mão** nos dois casos.

## Arquivos relevantes

- **Item:** `vanilla_Torch/Server/Item/Items/Furniture/Crude/Unique/Furniture_Crude_Torch.json`
  - `PlayerAnimationsId`: `"Torch"`
  - `InteractionConfig.Priorities.Secondary`: `MainHand: 1`, `OffHand: -1`
- **Conjunto:** `vanilla_Torch/Server/Item/Animations/Torch.json`
  - Sem `Parent`; todas as animações apontam para `Off_Handed/Torch/`.

## Ajuste no Horn

Foi adicionado ao Horn o mesmo **InteractionConfig.Priorities.Secondary** (`MainHand: 1`, `OffHand: -1`) para o engine tratar o Secondary de forma consistente. O Horn continua com **Parent "Item"** para a pose na mão principal (mão direita segurando, sem levantar a esquerda).
