# Pastas geradas na raiz do projeto (não são código do HorseFollow)

Estas pastas **aparecem** na raiz quando algo é executado no diretório do projeto (Hytale, Gradle, etc.) — **não** são extrações feitas pela regra do projeto.

**Regra:** `.cursor/rules/analise-extracoes.mdc` — **não extrair nada na pasta raiz**; extrações para análise vão em **`libs/Analise`**.

Estas pastas na raiz estão no `.gitignore`. Quando já existirem, servem como consulta.

---

## 1. `darwin/`, `freebsd/`, `linux/`, `win/`, `aix/`

**O que são:** Bibliotecas nativas (`.dylib`, `.so`, `.dll`) do **zstd-jni** (Zstandard). Cada pasta corresponde a um sistema/arquitetura:

- **darwin** — macOS (aarch64, x86_64)
- **freebsd** — FreeBSD (amd64, i386)
- **linux** — Linux
- **win** — Windows (aarch64, amd64, x86)
- **aix** — AIX (IBM Unix)

**Por que aparecem:** Alguma coisa que roda no diretório do projeto (servidor Hytale, Gradle, IDE, etc.) carrega um JAR que contém essas libs e as descompacta na **pasta atual**.

**Pode apagar?** Sim. São regeneradas quando necessário. Quando existirem, servem como consulta.

---

## 2. `com/`, `io/`, `it/`, `org/`, `META-INF/`

**O que são:** Conteúdo de JAR (classes `.class` e metadados) **extraído para a raiz** do projeto.

**Por que aparecem:** Alguém rodou `jar xf` (ou ferramenta) na raiz — **contra a regra do projeto**, que manda usar **libs/Analise** para extrações.

**Pode apagar?** Sim. Quando existirem, servem como consulta. Novas extrações: **libs/Analise**.

---

## 3. `migration/`

**O que são:** JSONs de mapeamento de blocos do Hytale. São arquivos de **migração entre versões** de blocos.

**Por que aparecem:** Podem ser gerados ou copiados quando o servidor Hytale (ou alguma ferramenta) roda no diretório do projeto.

**Pode apagar?** Sim, se não estiver usando. Quando existir, serve como referência.

---

## Resumo

- **Não extrair na raiz** — usar **libs/Analise** (regra do projeto).
- As pastas que já estão na raiz ficam no `.gitignore` e, quando existirem, podem ser usadas para consulta.
