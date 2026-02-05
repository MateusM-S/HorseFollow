# Relatório mínimo: engenharia reversa HyUI-0.5.10-all.jar

## Packages/classes relevantes

- **Entrypoint**: `au.ellie.hyui.builders.PageBuilder` — `pageForPlayer(playerRef).fromHtml(html).addEventListener(...).open(store)`
- **HTML/CSS → UI**: `au.ellie.hyui.html.HtmlParser`, `au.ellie.hyui.html.CssPreprocessor`, `au.ellie.hyui.html.TagHandler`
- **Handlers por tag**: `au.ellie.hyui.html.handlers` — `DivHandler`, `ButtonHandler`, `ImgHandler`, `InputHandler`, `LabelHandler`, `ProgressBarHandler`, `TabNavigationHandler`, `TabContentHandler`, `TextAreaHandler`, etc.
- **Eventos**: `au.ellie.hyui.events.UIContext` (valor de inputs/checkboxes), `DynamicPageDataReader`

## Tags HTML suportadas (mapeamento)

| HTML | Uso típico | Handler |
|------|------------|--------|
| div | container, layout | DivHandler |
| button | botão | ButtonHandler |
| input type="text" | texto | InputHandler |
| input type="checkbox" | checkbox | InputHandler |
| img | imagem | ImgHandler |
| progress | barra de progresso | ProgressBarHandler |
| nav data-tabs | abas | TabNavigationHandler |
| (tab content) | conteúdo da aba | TabContentHandler |
| p / label | texto | LabelHandler |

## Propriedades CSS usadas (HyUI/CssPreprocessor)

- `anchor-left`, `anchor-top`, `anchor-width`, `anchor-height` → Anchor no .ui
- `layout-mode` (middlecenter, Top, Left, etc.) → LayoutMode
- `padding`, `margin-*` → Padding/Margin
- `font-size`, `font-weight`, `color` → Style
- `background-color`, `background-image` (url) → Background
- `flex-weight` → FlexWeight

## Conversão HTML → nós UI Hytale

O HyUI usa builders (`GroupBuilder`, `TextFieldBuilder`, `TextButtonBuilder`, etc.) que geram comandos `UICommandBuilder` (append/set). O HTML é parseado (Jsoup), estilos aplicados via `TagHandler`/`ParsedStyles`, e cada handler produz nós .ui equivalentes (Group, TextField, Label, etc.) ou envia comandos para o cliente.

## Uso no HorseFollow

- `ConfigPage_HyUI.html`: divs, nav (tabs), input text (MountName, DistanceInput), input checkbox (DisableTeleport, DisableFollow), progress (LifeBar, StaminaBar), img, button (ResetName, SaveButton, Sair*).
- `PageBuilder.pageForPlayer(...).fromHtml(html).addEventListener(id, Activating, callback).open(store)`.
- Eventos: cliente envia dados; `ctx.getValue("id")` para input/checkbox.
