# Estudo: HyUI — documentação oficial

**Fonte:** [HyUI Docs](https://hyui.gitbook.io/docs)  
**Data do estudo:** 2025-01-31

---

## 1. O que é HyUI

**HyUI** é uma biblioteca Java para criar e gerenciar **interfaces de usuário customizadas** em servidores Hytale. Ela fica entre o protocolo de UI “cru” do Hytale e abstrações de alto nível, permitindo construir UIs complexas e interativas de duas formas:

- **HYUIML** — sintaxe declarativa tipo HTML/CSS
- **API fluente em Java** — builders encadeados (PageBuilder, ButtonBuilder, etc.)

Uso típico: painéis de admin, HUDs persistentes, menus de configuração (como a Config do HorseFollow).

---

## 2. Recursos principais

| Recurso | Descrição |
|--------|-----------|
| **HYUIML (HTML/CSS)** | Markup tipo HTML com estilos CSS, templating e componentes reutilizáveis |
| **Fluent Builder API** | Hierarquias de UI (Groups, Buttons, Labels, etc.) via encadeamento de métodos |
| **Multi-HUD** | Vários HUDs ao mesmo tempo; HyUI gerencia o “slot único” do Hytale |
| **Injeção dinâmica** | Carregar `.ui` base e injetar elementos em seletores em runtime |
| **Eventos** | Ligar lógica no servidor a eventos de UI com lambdas e `UIContext` |
| **Refresh periódico** | Atualização em lote de HUDs com baixo custo |
| **Escape hatches** | Acesso a `UICommandBuilder` para propriedades não cobertas pela API |

---

## 3. Instalação (Gradle)

Projeto de exemplo: <https://github.com/Elliesaur/Hytale-Example-UI-Project>

Via Cursemaven:

```groovy
repositories {
    maven { url "https://www.cursemaven.com" }
}

dependencies {
    // Project ID: 1431415
    implementation "curse.maven:hyui-1431415:<file-id>"
}
```

**Requisitos:** Hytale Server como dependência, Java 25 (ou versão compatível), jsoup (já no JAR), MultipleHUD opcional (código incluído no JAR).

---

## 4. Conceitos rápidos

- **Builders:** Tudo é construído com builders fluentes (`ButtonBuilder`, `LabelBuilder`, etc.).
- **Builders “detached”:** `detachedPage()` / `detachedHud()` para montar a UI antes de ter referência do jogador.
- **IDs / seletores:** `.withId("my-id")` no Java; `id="my-id"` no HYUIML — usados para eventos e `getById`.
- **Sanitização de IDs:** O Hytale só aceita IDs alfanuméricos; HyUI sanitiza (ex.: `my-button` → algo como `HYUUIDmybutton0`). Em Java, use sempre o ID **original** (`my-button`) em `getById` e `addEventListener`.

---

## 5. HYUIML — visão geral

HYUIML é uma linguagem de marcação **parecida com HTML** que o HyUI converte em chamadas da API de builders.

### 5.1 Uso básico

```java
String html = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="My Menu">
            <div class="container-contents">
                <p>Welcome to the menu!</p>
                <button id="myBtn">Click Me</button>
                <img class="dynamic-image" src="https://example.invalid/render/PlayerName" />
            </div>
        </div>
    </div>
    """;

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .addEventListener("myBtn", CustomUIEventBindingType.Activating, (ctx) -> {
        playerRef.sendMessage(Message.raw("Clicked!"));
    })
    .open(store);
```

### 5.2 Classes de layout especiais (para `<div>`)

| Classe | Função |
|--------|--------|
| **page-overlay** | Envolve os filhos em um `PageOverlay` do Hytale (tela cheia, escurecimento de fundo). Costuma ser a raiz da UI. |
| **container** | Container padrão (frame simples). |
| **decorated-container** | Container com frame “decorado”. |
| **container-title** | Filho de `.container`; conteúdo vai na área **#Title**. |
| **container-contents** | Filho de `.container`; conteúdo vai na área **#Content**. |
| **tab-content** | Conteúdo de aba; usar `data-hyui-tab-id` para ligar à aba. |
| **item-grid** | Grid de slots de item. |
| **item-grid-slot** | Um slot dentro do `.item-grid`. |

O título do container/overlay é definido com **`data-hyui-title`**.

### 5.3 Tags e mapeamentos (resumo)

| Tag HYUIML | Builder HyUI |
|------------|--------------|
| `<div>` | GroupBuilder |
| `<div class="container">` | ContainerBuilder (frame simples) |
| `<div class="decorated-container">` | ContainerBuilder (frame decorado) |
| `<div class="tab-content">` | TabContentBuilder |
| `<p>`, `<label>` | LabelBuilder |
| `<button>` | ButtonBuilder (variantes: `back-button`, `secondary-button`, `custom-textbutton`, `custom-button`, etc.) |
| `<input type="text">`, `type="password"` | TextFieldBuilder |
| `<input type="number">` | NumberFieldBuilder |
| `<input type="range">` | SliderBuilder |
| `<input type="checkbox">` | CheckBoxBuilder |
| `<input type="color">` | ColorPickerBuilder |
| `<input type="reset">` | CancelTextButton |
| `<progress>` | ProgressBarBuilder; `class="circular-progress"` → barra circular |
| `<span class="item-icon">` | ItemIconBuilder (`data-hyui-item-id`) |
| `<span class="item-slot">` | ItemSlotBuilder |
| `<div class="item-grid">` | ItemGridBuilder |
| `<div class="item-grid-slot">` | ItemGridSlot |
| `<img>` | ImageBuilder (`src` = caminho) |
| `<img class="dynamic-image">` | DynamicImageBuilder (PNG por URL em runtime) |
| `<hyvatar>` | HyvatarImageBuilder (avatar Hyvatar) |
| `<select>`, `<option>` | DropdownBoxBuilder |
| `<sprite>` | SpriteBuilder |
| `<nav class="tabs">` | TabNavigationBuilder |
| `<textarea>` | TextFieldBuilder (multiline) |

### 5.4 Atributos importantes

- **id** — Para eventos e `getById`.
- **class** — Estilos CSS e variantes (ex.: botões).
- **value** — Valor inicial em inputs; em `<select>` deve coincidir com um `<option value="...">`.
- **data-hyui-title** — Título do container/overlay.
- **data-hyui-tooltiptext** — Tooltip.
- **data-hyui-item-id** — ID do item (ícone/slot).
- **data-hyui-*** — Vários outros (barra de progresso, dropdown, tabs, grid, etc.); ver doc de [HYUIML](https://hyui.gitbook.io/docs/home/hyuiml-htmlish-in-hytale).

Para **tabs:** em `<nav class="tabs">` usar `data-tabs` (ex.: `tabId:Label` ou `tabId:Label:contentId`) e `data-selected` para a aba inicial; em cada bloco de conteúdo usar `data-hyui-tab-id`.

### 5.5 Estilo com CSS

É possível usar um bloco `<style>` no início do HYUIML. Propriedades suportadas incluem:

- **Texto:** `color`, `font-size`, `font-weight`, `text-transform`, etc.
- **Referência de estilo Hytale:** `hyui-style-reference: "Common.ui" "DefaultLabelStyle"`.
- **Layout:** `text-align` / `layout-mode` / `layout` (LayoutMode do Hytale), `vertical-align`, `horizontal-align`, `align`, `visibility` / `display`, `flex-weight`.
- **Ancoras:** `anchor-left`, `anchor-top`, `anchor-width`, `anchor-height`, etc.
- **Background:** `background-image: url('...')`, `background-color` (hex ou rgba), com opção de bordas.

**Unidades:** HyUI remove unidades (px, rem, %); usar números puros (ex.: `font-size: 16;`).

### 5.6 Imagens

- **Assets:** Caminhos relativos ao **`Common/UI/Custom`** do mod. Para resolução alta, o arquivo deve terminar em **`@2x.png`** (ex.: `lizard@2x.png`).
- **Dynamic images:** `<img class="dynamic-image" src="URL">` — baixa PNG em runtime; limite de 10 por página por jogador; cache de 15 s.
- **Hyvatar:** `<hyvatar username="..." render="head|full|cape" size="..." rotate="...">`.

### 5.7 Eventos

Eventos são ligados no **PageBuilder** (ou HudBuilder) pelos **IDs** do markup:

```java
builder.addEventListener("my-button", CustomUIEventBindingType.Activating, (ignored, ctx) -> {
    playerRef.sendMessage(Message.raw("Button clicked!"));
});
```

Elementos carregados por **`.fromFile`** (arquivo `.ui` cru) **não** suportam `addEventListener`; a interação via eventos é para elementos definidos em HYUIML (ou construídos pela API).

### 5.8 Limitações

- **CSS:** Apenas as propriedades listadas na doc; layout “clássico” (flexbox completo, grid, etc.) não existe.
- **Sem scripting:** Tags `<script>` são ignoradas; lógica só em Java.
- **Aninhamento:** Alguns macros do Hytale podem se comportar mal com muitas camadas.

---

## 6. Builders (tabela de referência)

| Builder | Uso |
|--------|-----|
| PageBuilder | Páginas full-screen; carrega arquivo ou HTML; ciclo de vida |
| HudBuilder | HUDs persistentes; multi-HUD e refresh |
| GroupBuilder | Container de layout |
| ContainerBuilder | Janela padrão do Hytale |
| PageOverlayBuilder | Overlay full-screen |
| TabNavigationBuilder | Barra de abas |
| TabContentBuilder | Conteúdo de uma aba |
| ButtonBuilder | Botões (texto, voltar, custom) |
| LabelBuilder | Texto dinâmico |
| ImageBuilder | Imagem de asset |
| DynamicImageBuilder | Imagem PNG por URL |
| TextFieldBuilder / NumberFieldBuilder | Campos de texto e número |
| SliderBuilder, CheckBoxBuilder, ColorPickerBuilder | Slider, checkbox, cor |
| ProgressBarBuilder | Barra (e circular) |
| ItemIconBuilder, ItemSlotBuilder, ItemGridBuilder | Ícone, slot e grid de itens |
| TimerLabelBuilder | Label com timer |
| SpriteBuilder | Sprite animado |

---

## 7. HUD

```java
HudBuilder.hudForPlayer(playerRef)
    .fromHtml("<div style='anchor-top: 10; anchor-left: 10;'><p>Health: 100</p></div>")
    .show(store);
```

HUDs são elementos persistentes na tela; o HyUI cuida da coexistência com outros mods no único slot de HUD do Hytale.

---

## 8. Relação com o HorseFollow

- O HorseFollow já tem **Custom UI** em `Common/UI/Custom/Pages/ConfigPage.ui` (config simples: distância de teleporte, Save/Cancel). A regra do projeto cita que uma versão com abas causava crash ao carregar; a versão atual é simplificada.
- **HyUI** seria uma alternativa ou complemento:
  - **Se usar HyUI:** dá para construir a mesma tela (e até abas) via **HYUIML** ou **builders em Java**, com eventos ligados no servidor (ex.: salvar distância, cancelar). Não depende do mesmo formato `.ui` que hoje quebra com abas.
  - **Se manter só .ui:** o estudo do HyUI serve para quando quiser migrar a Config para uma lib que oferece abas, HUDs e injeção dinâmica sem depender do parser nativo do cliente para `.ui` complexos.

Documentação completa, exemplos e tutoriais (Page Building, Item Grids, Template Processor, Tab Navigation): [HyUI Docs](https://hyui.gitbook.io/docs).  
Suporte: Discord [HyUI](https://discord.gg/NYeK9JqmNB).
