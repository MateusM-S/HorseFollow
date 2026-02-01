# Estudo detalhado: HyUI — todas as abas e como funciona

**Fonte:** [HyUI Docs](https://hyui.gitbook.io/docs)  
**Data:** 2025-01-31

---

## 1. Estrutura obrigatória para páginas com abas

O tutorial [Using Tabs](https://hyui.gitbook.io/docs/tutorials/tutorial-tab-navigation/using-tabs) mostra a **única estrutura que a doc garante**:

```html
<div class="page-overlay">
  <div class="decorated-container" data-hyui-title="Workshop">
    <div class="container-contents" style="layout-mode: Top; padding: 6;">
      <nav id="workshop-tabs" class="tabs" data-tabs="blueprints:Blueprints:blueprints-content,materials:Materials:materials-content,tools:Tools:tools-content" data-selected="blueprints"></nav>
      <div id="blueprints-content" class="tab-content" data-hyui-tab-id="blueprints"><p>...</p></div>
      <div id="materials-content" class="tab-content" data-hyui-tab-id="materials"><p>...</p></div>
      <div id="tools-content" class="tab-content" data-hyui-tab-id="tools"><p>...</p></div>
    </div>
  </div>
</div>
```

Regras críticas:

1. **Um único container** por página: `page-overlay` → `container` (ou `decorated-container`) → `container-contents`.
2. **Nav e tab-content são irmãos** dentro de `container-contents`, não fora do container.
3. **data-tabs** no nav no formato **tabId:Label:contentId** (três partes) para ligar aba ao bloco de conteúdo.
4. **data-hyui-tab-id** em cada `div.tab-content` deve coincidir com o `tabId` do `data-tabs`.

Se colocarmos o nav **fora** do container (como irmão do container), o PageOverlay/Container do Hytale pode receber apenas um filho e o layout quebra ou não altera nada in-game.

---

## 2. Como obter “menu à esquerda, caixa à direita” dentro da doc

Para manter a estrutura válida e ainda ter “sidebar à esquerda”:

- **container-contents** deve ser **um único** `div` com **layout-mode: Left**.
- **Primeiro filho:** div da sidebar (nav) com **anchor-width** fixo (ex.: 100).
- **Segundo filho:** div que envolve os **tab-content** (flex-weight: 1), contendo os três `div.tab-content`.

Assim, nav e conteúdo ficam **dentro** do mesmo container, mas visualmente lado a lado (nav à esquerda, conteúdo à direita).

---

## 3. Classes especiais (Special Layout Classes)

| Classe | Uso |
|--------|-----|
| `page-overlay` | Raiz; envolve em PageOverlay (tela cheia, dimming). |
| `container` | Frame padrão (Container). |
| `decorated-container` | Frame “decorado”. |
| `container-title` | Filho do container; conteúdo vai em **#Title**. |
| `container-contents` | Filho do container; conteúdo vai em **#Content**. |
| `tab-content` | Bloco de conteúdo de aba; usar `data-hyui-tab-id`. |

Se não usar `container-contents`, elementos diretos no container vão para **#Content** por padrão.

---

## 4. Tags e mapeamentos (resumo)

- `<div>` → GroupBuilder  
- `<div class="container">` / `decorated-container` → ContainerBuilder  
- `<div class="tab-content">` → TabContentBuilder  
- `<p>`, `<label>` → LabelBuilder  
- `<button>` → ButtonBuilder (classes: `back-button`, `secondary-button`, `small-secondary-button`, `custom-textbutton`, etc.) — **não aceita filhos** (ex.: não colocar `<img>` dentro; usar texto ou ícone via atributo se a API permitir). AppendInline em botão causa crash: "selected element doesn't accept children".  
- `<input type="text">` → TextFieldBuilder (exige `value` para eventos)  
- `<input type="range">` → SliderBuilder  
- `<input type="checkbox">` → CheckBoxBuilder  
- `<progress>` → ProgressBarBuilder  
- `<img>` → ImageBuilder (`src` relativo a **Common/UI/Custom**; Hytale espera arquivo **@2x.png** para alta resolução)  
- `<nav class="tabs">` → TabNavigationBuilder  

---

## 5. Layout e CSS suportado

- **layout-mode** / **layout** / **text-align:** Top, Left, Right, Center, etc. (LayoutMode do Hytale). **Não usar `text-align` em `<p>`/labels** — o cliente pode crashar com "CustomUI Set command couldn't set value. Selector: #HYUUIDLabel*.Style".
- **anchor-***: anchor-left, anchor-top, anchor-width, anchor-height.
- **flex-weight:** peso no layout (a partir de v0.5.0 aplicado ao grupo que envolve o elemento).
- **padding**, **color**, **font-size**, **font-weight**, **background-color**, **background-image** (com restrições).
- Unidades (px, em, %) são removidas; usar números puros.

---

## 6. Eventos e Java

- Eventos só funcionam em elementos definidos em **HYUIML** (não em `.fromFile()`).
- `addEventListener("id-do-elemento", CustomUIEventBindingType.Activating, (data, ctx) -> { ... })`.
- **UIContext:** `ctx.getValue("id")`, `ctx.getPage()`, `ctx.getById("id", BuilderClass)`.
- Fechar página deve ser feito na **world thread** (ex.: passar `Consumer<Runnable>` e chamar `runOnWorldThread.accept(() -> closePage(...))`).

---

## 7. Validação (Element Validation)

- HyUI **não** valida `.ui` em `.fromFile()` (ainda).
- Em HYUIML, propriedades/estilos inválidos falham em silêncio (excluídos, sem crash).
- Container tem **#Content** e **#Title** como Groups; usar apenas as classes documentadas evita estrutura inválida.

---

## 8. Aplicação ao HorseFollow

- Usar **uma única** árvore: `page-overlay` → `container` ou `decorated-container` → **um** `container-contents`.
- Para “menu à esquerda, caixa à direita”: `container-contents` com **layout-mode: Left**, primeiro filho = div da nav (anchor-width: 100), segundo filho = div com os três `tab-content` (flex-weight: 1).
- Manter **data-tabs="menu:Menu:menu-content,config:Configuração:config-content,about:Sobre:about-content"** e **data-hyui-tab-id** em cada tab-content.
- Ícones: `Common/UI/Custom` com arquivos **@2x.png**; em HYUIML usar `src="Menu.png"` etc.

---

## 9. Por que “não alterou nada” in-game (estrutura antiga)

A versão anterior usava:

- `page-overlay` → **div (layout Left)** → **[sidebar com nav]** + **[container]**

Ou seja: o **nav era irmão do container**, fora da “caixa”. O HyUI/Hytale espera que o **filho direto** do page-overlay seja **um** container (ou um grupo que contenha um container). Quando há dois filhos (sidebar + container), o macro PageOverlay pode aceitar só o primeiro ou montar o layout de forma que nada mude visivelmente. Por isso in-game “não alterou nada”.

**Solução:** manter **um único** container; colocar **nav e conteúdo dentro** de `container-contents` e usar **layout-mode: Left** em `container-contents`, com o nav no primeiro filho (sidebar) e os tab-content no segundo (área principal). Assim o posicionamento “menu à esquerda, conteúdo à direita” é respeitado **dentro** da doc.

---

Referências: [HyUI](https://hyui.gitbook.io/docs), [HYUIML](https://hyui.gitbook.io/docs/home/hyuiml-htmlish-in-hytale), [Using Tabs](https://hyui.gitbook.io/docs/tutorials/tutorial-tab-navigation/using-tabs), [Page Building](https://hyui.gitbook.io/docs/tutorials/tutorial-page-building), [Element Validation](https://hyui.gitbook.io/docs/home/element-validation).
