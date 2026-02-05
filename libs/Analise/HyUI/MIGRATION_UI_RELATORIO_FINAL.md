# Migração ConfigPage: HyUI → .ui nativo — Relatório final

## Resumo

- **ConfigPage** passou de HTML/CSS (HyUI) para **.ui nativo** do Hytale.
- **HyUI** foi removido do fluxo e da dependência do projeto para esta página.
- **Build** concluído com sucesso; JAR gerado sem HyUI.

## Arquivos alterados/criados (caminho completo)

### Criados
- `src/main/resources/Common/UI/Custom/Pages/ConfigPage.ui` — UI nativa (abas Menu, Configuração, Sobre)
- `src/main/java/com/horsefollow/ui/ConfigPageNative.java` — Página interativa (InteractiveCustomUIPage)
- `tools/ui_migration_anchor.ps1` — Script de âncora/rollback (Anchor, Rollback, BuildCheck)
- `libs/Analise/HyUI/Relatorio_HyUI_Engenharia_Reversa.md` — Relatório mínimo da engenharia reversa do HyUI
- `libs/Analise/HyUI/MIGRATION_UI_RELATORIO_FINAL.md` — Este relatório
- `backup/` — Pasta para zips de backup (ANCHOR_1.zip etc.)

### Alterados
- `build.gradle` — Removida dependência `implementation files('libs/HyUI-0.5.10-all.jar')` e trecho do `jar` que incluía runtimeClasspath (fat JAR com HyUI)
- `src/main/java/com/horsefollow/commands/HorseFollowCommand.java` — Passa a usar `ConfigPageNative.open(...)` em vez de `ConfigPageHyUI.open(...)`

### Removidos
- `src/main/java/com/horsefollow/ui/ConfigPageHyUI.java` — Substituído por ConfigPageNative

### Mantidos (não usados pela ConfigPage; podem ser removidos manualmente se desejado)
- `src/main/resources/Common/UI/Custom/Pages/ConfigPage_HyUI.html` — Pode ser removido ou mantido como referência

## O que foi removido do HyUI

- Dependência em tempo de compilação e em tempo de execução: **HyUI-0.5.10-all.jar** não é mais dependência do projeto.
- Uso de **PageBuilder**, **fromHtml(html)**, **addEventListener** do HyUI e todo o fluxo HTML → conversão → UI.
- Inclusão do HyUI no JAR final (fat JAR): o JAR gerado é menor e não contém mais as classes e recursos do HyUI.

## Redução de tamanho

- **Antes**: JAR incluía `HyUI-0.5.10-all.jar` (e possivelmente jsoup) via `from { configurations.runtimeClasspath ... }`.
- **Depois**: JAR contém apenas `sourceSets.main.output` (classes e recursos do HorseFollow).
- A redução equivale ao tamanho do HyUI-0.5.10-all.jar (e dependências empacotadas). Para valores exatos, compare `build/libs/HorseFollow-*.jar` antes e depois (ou confira o tamanho do HyUI no `libs/`).

## Build e execução

```powershell
# Build
.\gradlew.bat build

# JAR gerado
# build\libs\HorseFollow-<version>.jar

# Instalação local (conforme projeto)
# .\Scripts\BuildAndInstallToHytale.ps1
```

## Rollback

- **Git**: branch `ui-migration-configpage`; commit `ANCHOR_1_PRE_MIGRATION` é o estado pré-migração.
- **Script**: `.\tools\ui_migration_anchor.ps1 -Action Rollback` — faz `git reset --hard HEAD~1` na branch e informa como restaurar do zip em `backup/`.
- **BuildCheck**: `.\tools\ui_migration_anchor.ps1 -Action BuildCheck` — roda o build e, se falhar, executa rollback automático.

## Comportamento da UI nativa

- **Abas**: Menu, Configuração, Sobre — troca de aba não perde estado (valores e toggles ficam no FollowService).
- **Menu**: nome da montaria (TextField), botão Reset para aplicar nome, labels de vida/estamina/velocidade, botão SAIR.
- **Configuração**: distância do teleporte (TextField), botões SIM/NÃO para “Desativar teleporte” e “Desativar acompanhamento”, SALVAR e SAIR. Ao salvar: persiste distância e fecha a UI.
- **Sobre**: texto fixo e SAIR.
- **Persistência**: config (distância, teleporte desativado, follow desativado) é lida/escrita pelo FollowService; a UI apenas exibe e envia eventos.

## Critérios de aceite atendidos

- ConfigPage abre com .ui nativo, sem HyUI.
- Não há dependência de conversão HTML→UI para esta página.
- Build OK.
- Troca de abas mantém estado (servidor).
- Salvar persiste e fecha a UI; não trava botões.
- Script e procedimento de rollback disponíveis (tools/ui_migration_anchor.ps1 + git).
