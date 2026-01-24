# Changelog

## [1.3.0]
- Adiciona config persistente (`config.properties`) para distancia de teleporte, offset, cooldown e limite de TPs por tick.
- Usa a configuracao dinamica no `FollowService` e evita NPE quando o `store` e nulo.
- Unifica comandos em `/horsefollow` com subcomandos `distance`, `call` e `reload`.
- Implementa `/horsefollow help` com mensagens detalhadas via sistema de lang.
- Adiciona aliases em PT (`vincular`, `desvincular`, `distancia`, `chamar`, `resetar`) para os subcomandos.
- Atualiza arquivos de linguagem em `en-US` e `pt-BR` com novos textos de comando e ajuda.
- Remove comandos dedicados `ChamarCommand` e `ResetarCommand` (agora usam subcomandos).
- Habilita follow via flock ao vincular o cavalo e remove o flock ao desvincular.
- Ajusta o follow do cavalo (visao 20, follow 12, stop 3).
- Atualiza o TP: padrao 100 e `distance 0` desativa o teleporte automatico.
