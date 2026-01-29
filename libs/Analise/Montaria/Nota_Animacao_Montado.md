# Nota: Animação do animal quando tem jogador montado

**Data:** 29 de Janeiro de 2026  
**Projeto:** HorseFollow  

## Situação

Foi reportado um erro nas animações quando o animal tem um player montado — possivelmente algo no Template, não na textura. O padrão a conferir é o **Template_Animal_Neutral.json** (vanilla): o que acontece com o animal quando tem um jogador montado.

## Referências no projeto

- **Horse.json** (vanilla/mod): `"Reference": "Template_Animal_Neutral"` — cavalo “neutro” usa o template neutral.
- **Horse_Friendly.json** (mod): `"Reference": "Template_Animal_Tamed"` — cavalo vinculado usa o template tamed.

O **Template_Animal_Neutral** não está no repositório do mod; faz parte dos assets vanilla do Hytale. Para comparar o comportamento “quando montado”, é preciso abrir o **Template_Animal_Neutral.json** a partir dos assets oficiais (ex.: extrair do jogo ou da documentação).

## O que verificar no Template_Animal_Neutral (vanilla)

1. **Instruções condicionadas a “montado”**  
   Se existe um sensor/filtro do tipo “HasPassengers” / “MountedBy” / “HasRider” que desativa as instruções de Idle (wander, eat, flock follow) quando o NPC tem passageiro.

2. **Transição de estado ao montar**  
   Se ao ter um jogador montado o NPC muda de estado (ex.: Idle → “Mounted” ou “Riding”), e as instruções de Idle só rodam quando **não** está nesse estado.

3. **Limpeza de animação**  
   Se há uma instrução que, quando o NPC tem passageiro, faz algo como `PlayAnimation` slot `Status` (clear) para não manter animações de Idle/wander enquanto montado.

## Documentação Hytale (mounts)

- Quando montado, o engine define **MovementStates.mounting = true** e isso afeta animação, movimento e input.
- **MountedByComponent** no mount guarda a lista de passageiros; **NPCMountComponent** guarda o dono (player).
- Para “não fazer scan em montaria já vinculada” isso já foi tratado no código (**findTargetMount** exclui a montaria retornada por **getBoundHorse(playerRef)**).

## Próximos passos sugeridos

1. Obter **Template_Animal_Neutral.json** dos assets vanilla e comparar com **Template_Animal_Tamed.json** no mod, em especial:
   - instruções que tenham `Enabled` ou sensor relacionado a “montado” / “passengers”;
   - transições de estado que dependam de ter passageiro.
2. Se no neutral existir um padrão “quando tem passageiro, desabilitar Idle / limpar animação”, replicar esse padrão no **Template_Animal_Tamed** (ou no role que o usa, se for mais seguro).
3. Se na documentação ou no JAR aparecer o nome exato do sensor (ex.: `HasPassengers`, `MountedBy`), usar esse sensor no **Template_Animal_Tamed** para desabilitar as instruções de Idle quando o NPC tiver jogador montado.

---

## Ajuste aplicado (29 Jan 2026)

- **Template_Animal_Neutral.json** foi extraído do `Assets.zip` para `libs/Analise/vanilla__Template_Animal_Neutral/Server/NPC/Roles/_Core/Templates/`.
- No **Template_Animal_Neutral** (vanilla) **não** existe sensor/filtro explícito para “montado” ou “passengers”; o Neutral também não tem o bloco “HorseFollow: Follow flock leader”.
- No **Template_Animal_Tamed.json** do mod foram feitas duas alterações:
  1. **Instrução HorseFollow**: o `Enabled` passou a incluir `&& !hasPassengers()`, para que o follow do flock leader só rode quando o NPC **não** tiver passageiros.
  2. **Bloco de instruções do estado Idle** (wander, eat, grazing, daytime): foi adicionado `"Enabled": { "Compute": "!hasPassengers()" }`, para que todos os comportamentos de Idle (e animações associadas) fiquem desativados quando o animal tiver jogador montado.
- **Dependência**: o ajuste usa a função de compute **`hasPassengers()`** (e `!hasPassengers()`). Se o engine do Hytale não expuser essa função, o mod pode falhar ao carregar o role ou a condição pode não funcionar; nesse caso será necessário reverter ou usar outro mecanismo (ex.: filtro Self, se existir).
