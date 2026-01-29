# Consultas — biblioteca de dados para a IA

Pasta de referência para consulta durante o desenvolvimento do HorseFollow.

## Regra do projeto (extrações)

**Não extrair nada na pasta raiz.** Conforme `.cursor/rules/analise-extracoes.mdc`:

- Extrações (JAR/ZIP/logs/relatórios etc.) para inspeção/comparação **não** devem ficar na raiz nem em outras pastas.
- Tudo deve ir em **`libs/Analise`**, com nomes/prefixos claros e estrutura organizada.

## Conteúdo

- **pastas-geradas-na-raiz.md** — explicação das pastas que *aparecem* na raiz (com/, io/, darwin/, etc.): o que são, por que existem. Essas pastas **não** são extraídas para lá de propósito pela regra; surgem quando o Hytale/Gradle/etc. roda no diretório do projeto. Quando já existirem, servem como consulta.

## Pastas na raiz (quando existirem)

As pastas `com/`, `io/`, `darwin/`, etc. na raiz **não** são criadas por extração nossa (a regra proíbe). São resultado de algo rodando no diretório do projeto. Quando já existirem, servem como consulta; **novas extrações para análise vão em libs/Analise**.
