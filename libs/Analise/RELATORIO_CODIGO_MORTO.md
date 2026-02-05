# Relatório: Código morto / não utilizado

## Resumo

Análise do código Java do HorseFollow para identificar métodos, classes e recursos não utilizados.

---

## 1. Código Java morto (nunca chamado)

### HorseFollowPlugin
| Item | Motivo |
|------|--------|
| `getInstance()` | Nunca chamado em nenhum lugar. Provavelmente era para a UI ConfigPage (deletada). |

### FollowService
| Item | Motivo |
|------|--------|
| `getBoundPlayers()` | Nunca chamado. Retornava cópia do mapa de vínculos. |
| `getMountTypeName()` | Nunca chamado. Era para exibir tipo da montaria na UI (ConfigPage deletada). |
| `getMountDisplayStats()` | Nunca chamado. Era para exibir HP/Stamina/Velocidade na UI (ConfigPage deletada). |
| `MountDisplayStats` (classe interna) | Só usado por `getMountDisplayStats()`, que é morto. |
| `runOnWorld()` | Nunca chamado. ItemConsume usa seu próprio `runOnWorldThread()`. |

### ItemConsume
| Item | Motivo |
|------|--------|
| `ItemConsume(FollowService)` | Construtor com 1 arg nunca usado. Plugin sempre usa o de 2 args. |
| `onPlayerDisconnect()` | Nunca chamado. O plugin não registra listener de desconexão. |

---

## 2. Código potencialmente útil (não morto, mas sem uso atual)

| Item | Observação |
|------|------------|
| `onPlayerDisconnect()` | Seria útil para limpar estado ao desconectar e evitar acúmulo em memória. Requer registro de listener no plugin. |
| `getInstance()` | Pode ser útil se uma UI ou outro sistema precisar acessar o plugin. |

---

## 3. Duplicação de lógica (não é morto, mas redundante)

| Local | Descrição |
|-------|-----------|
| `worldExecute` / `runOnWorld` / `runOnWorldThread` | Implementações quase idênticas em FollowService, HorseFollowCommand e ItemConsume. Poderia ser extraído para uma classe utilitária. |
| `findMountFromPassengers` / `findNpcMountByOwner` | Duplicados em FollowService (privado) e HorseFollowCommand (para o case "bind" do comando). Necessário pois FollowService não expõe esses métodos. |

---

## 4. Recursos (assets)

Todos os recursos em `src/main/resources/` parecem referenciados:
- SoundEvents (Horn, Consume_Bread) — usados por ItemConsume
- Items (Horn, Horse_Feed, Ram_Feed) — usados pelo gameplay
- server.lang — usados por Localization
- Recipes/Salvage — Salvage_Horn

---

## 5. Artefatos no source (não deveriam estar em src/)

| Caminho | Ação sugerida |
|---------|---------------|
| `src/main/java/com/horsefollow/commands/HorseFollowCommand.class` | Arquivo compilado em `src/` — deveria ser apenas em `build/`. Pode ser removido ou adicionado ao .gitignore. |

---

## 6. Alterações realizadas (02/02/2025)

- Removido `HorseFollowPlugin.getInstance()` e variável `instance`
- Removido `FollowService.getBoundPlayers()`
- Removido `FollowService.getMountTypeName()`
- Removido `FollowService.getMountDisplayStats()` e classe `MountDisplayStats`
- Removidos `tryGetEntityStatMap()` e `tryGetStatValue()` (usados apenas por getMountDisplayStats)
- Removido `FollowService.runOnWorld()`
- Removido construtor `ItemConsume(FollowService)` (1 arg)
- Removido `ItemConsume.onPlayerDisconnect()`
- Removido `HorseFollowCommand.class` (artefato em src/)

O build foi verificado e compila corretamente.

---

*Relatório gerado em 02/02/2025*
