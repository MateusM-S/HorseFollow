# Relatório: Salvage do Horn — Análise com Base em Provas

## Resumo

Foram analisados **3 itens vanilla** que funcionam na Bench_Salvage e comparados com o **Horn** do mod HorseFollow. O problema identificado foi que o **Salvage_Horn.json não estava no JAR** antes de um `gradle clean build` — a pasta `Recipes` pode não ter sido incluída em builds anteriores.

---

## 1. Itens Analisados (vanilla, funcionam no Salvage)

| Item | Receita Salvage | Caminho no Assets |
|------|-----------------|-------------------|
| **Weapon_Sword_Copper** | Salvage_Weapon_Sword_Copper.json | Server/Item/Items/Weapon/Sword/Weapon_Sword_Copper.json |
| **Armor_Copper_Head** | Salvage_Armor_Copper_Head.json | Server/Item/Items/Armor/Copper/Armor_Copper_Head.json |
| **Rock_Stone_Cobble** | Salvage_Rock_Stone_Cobble.json | Server/Item/Items/Rock/Stone/Rock_Stone_Cobble.json |

Arquivos extraídos em `libs/Analise/Salvage/`.

---

## 2. O que os 3 itens têm em comum

### 2.1 Estrutura da receita Salvage (todos idênticos)

```json
{
  "Input": [{ "ItemId": "<ID_DO_ITEM>", "Quantity": 1 }],
  "PrimaryOutput": { "ItemId": "...", "Quantity": N },
  "Output": [...],
  "BenchRequirement": [{ "Type": "Processing", "Id": "Salvagebench" }],
  "TimeSeconds": 4
}
```

- **Bench ID**: `Salvagebench` (não `Bench_Salvage`)
- **Type**: `Processing`

### 2.2 Nos itens (JSON do item)

| Propriedade | Weapon_Sword_Copper | Armor_Copper_Head | Rock_Stone_Cobble |
|-------------|---------------------|-------------------|-------------------|
| Recipe (crafting) | Sim | Sim | **Não** |
| ResourceTypes | Não (herda?) | Não (herda?) | Sim: Rock, Rock_Stone |
| Tags | (herda) | Armor, Copper | Rock, Stone |
| Categories | (herda) | (herda) | Blocks.Rocks |

**Conclusão**: Não há propriedade única obrigatória nos itens. Rock_Stone_Cobble nem tem Recipe de crafting.

### 2.3 Bench_Salvage (Server/Item/Items/Bench/Bench_Salvage.json)

```json
"Bench": {
  "Type": "Processing",
  "Input": [{ "FilterValidIngredients": true }],
  "Id": "Salvagebench"
}
```

`FilterValidIngredients: true` indica que a bancada filtra para mostrar apenas itens que possuem uma **receita de Processing** com `Id: Salvagebench` em que o item é o Input.

---

## 3. Horn do HorseFollow — Comparação

| Aspecto | Horn (HorseFollow) | Itens vanilla |
|---------|--------------------|----------------|
| Salvage_Horn.json | Sim, em Server/Item/Recipes/Salvage/ | Idêntica pasta |
| BenchRequirement Id | Salvagebench | Salvagebench |
| BenchRequirement Type | Processing | Processing |
| Input ItemId | "Horn" | ID do item |
| Item em Items/ | Horn.json | Em subpastas (Weapon/, Armor/, Rock/) |

O Salvage_Horn segue o mesmo padrão das receitas vanilla.

---

## 4. Problema identificado: Recipes não estava no JAR

**Evidência**: Antes de `gradle clean build`, a pasta `build/resources/main/Server/Item/` não continha `Recipes/`.

**Evidência**: Após `gradle clean build`, o JAR passa a incluir:

```
Server/Item/Recipes/Salvage/Salvage_Horn.json
```

**Causa provável**: O Salvage_Horn.json foi criado depois do último build, ou a pasta Recipes não existia na versão usada para gerar o JAR. Sem a receita no JAR, o jogo não reconhece o Horn como item salvageável.

---

## 5. Verificação do Salvage_Horn.json do HorseFollow

```json
{
  "Input": [{ "ItemId": "Horn", "Quantity": 1 }],
  "PrimaryOutput": { "ItemId": "Ingredient_Chitin_Sturdy", "Quantity": 2 },
  "Output": [{ "ItemId": "Ingredient_Chitin_Sturdy", "Quantity": 2 }],
  "BenchRequirement": [{ "Type": "Processing", "Id": "Salvagebench" }],
  "TimeSeconds": 2
}
```

Estrutura coerente com as receitas vanilla.

---

## 6. Recomendações

1. **Fazer um novo build e reinstalar**: `gradle clean build` e instalar o novo JAR no Hytale.
2. **Testar de novo**: Com o Salvage_Horn.json no JAR, o Horn deve ser aceito na bancada.
3. **Se ainda falhar**: Conferir nos logs do servidor se `Salvage_Horn` aparece em `CraftingRecipe` carregadas.

---

## 7. IDs no Hytale

O ID do item é derivado do caminho do arquivo. Para `Server/Item/Items/Horn.json`, o ID é **Horn**. O Salvage_Horn usa `ItemId: "Horn"`, compatível.

---

*Relatório gerado em 02/02/2025 com base em análise dos Assets vanilla do Hytale.*
