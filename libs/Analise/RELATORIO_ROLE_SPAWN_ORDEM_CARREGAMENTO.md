# Relatório: Role, Spawn e erro "does not exist as a role"

Pesquisa sobre criação de entidades, regra (role), spawn e o erro `Black_Horse does not exist as a role!` / Tag Set / NPCSpawn.

## Fontes

- [Hytale Modding – Spawning NPCs](https://hytalemodding.dev/en/docs/guides/plugin/spawning-npcs)
- [Hytale Server Docs – Assets & Registry](https://hytale-docs.pages.dev/modding/content/assets/)
- [vulpeslab/hytale-docs – NPC Spawning and Roles](https://deepwiki.com/vulpeslab/hytale-docs/6.4-npc-spawning-and-roles)
- Web search: "does not exist as a role", load order, TagSet, WorldNPCSpawn

---

## 1. Regra (role) e spawn

- **Role** = container de comportamento do NPC (AI, stats, animações, instruções, etc.). Cada NPC tem **um** role por vez (pode trocar em runtime).
- **Spawning:** o jogo usa `NPCPlugin.spawnNPC(store, "RoleId", ...)`. O segundo parâmetro é o **ID do role**; esse ID precisa já existir no registro de roles.
- **WorldNPCSpawn (JSON):** cada entrada em `NPCs` tem um campo **`Id`** que é **um único** role ID (ex.: `"Id": "Horse"` ou `"Id": "Black_Horse"`). Não existe sintaxe para "vários roles" ou "grupo" no mesmo `Id` — por exemplo `"Id": "Horse, Black_Horse"` é interpretado como um role literal chamado "Horse, Black_Horse" e gera erro.

**Conclusão:** no spawn, uma entrada = um role ID. Para Horse e Black_Horse, são duas entradas separadas.

---

## 2. Ordem: role antes do spawn

- Na documentação (NPC Spawning and Roles): **"Roles must be defined and registered before NPCs can spawn"** — o role precisa estar no registro antes de o spawn poder referenciá-lo.
- Fluxo típico de criação de NPC pelo spawn: (1) Instanciação da entidade no ECS, (2) Componentes base, (3) **Atribuição do role** (o role já deve existir), (4) Inicialização da AI, (5) Integração no mundo.
- A validação que quebra no nosso caso: `SpawningPlugin.onWorldNPCSpawnsLoaded` (reage ao `LoadedAssetsEvent`) chama `NPCPlugin.validateSpawnableRole(roleId)`. Se o role não existir nesse momento → `IllegalArgumentException: X does not exist as a role!`.

**Conclusão:** o jogo **espera** que todos os roles referenciados no spawn já existam no momento da validação. O nosso erro ocorre porque, na ordem em que os assets do **mod** são processados, essa validação roda antes do role `Black_Horse` do mod estar registrado.

---

## 3. Ordem de carregamento de assets (a “ponte”)

- O **AssetRegistry** do Hytale permite ordenar tipos de asset com:
  - `.loadsAfter(DependencyClass.class)` — este asset type carrega depois do outro
  - `.loadsBefore(DependentClass.class)` — este asset type carrega antes do outro
- Isso é configurado quando um **plugin** (do jogo ou do mod) **registra** o asset store, no `setup()`. Ou seja: a ordem é definida pelos módulos built-in (NPC, TagSet, Spawning, etc.); o nosso mod não registra esses stores, só contribui com JSON no pack.
- Na prática, ao mesclar o pack do mod com o base, **Tag Set (Groups)** e **WorldNPCSpawn** são validados (ou os eventos disparam) antes de todos os **Roles** do pack do mod estarem no registro → daí "Black_Horse does not exist as a role!" e "Tag Set 'Horse' references 'Black_Horse' which ... does not otherwise exist".

**Conclusão:** a “ponte” é a ordem interna de processamento de tipos de asset (Roles vs Groups vs WorldNPCSpawn). Não temos como alterar essa ordem pelo mod; usar `LoadBefore` para carregar o pack do mod antes do base foi descartado por risco de quebrar a base do jogo.

---

## 4. Tag Set (Group) e o aviso

- **Tag Set / NPCGroup:** grupos que referenciam roles por ID ou por pattern (ex.: `"Horse*"`). O grupo `Horse` no nosso mod tem `IncludeRoles: ["Horse*", "Black_Horse"]`.
- Quando o Tag Set é validado, ele verifica se cada referência existe (como role ou como pattern). Se naquele momento `Black_Horse` ainda não foi registrado → aviso "references 'Black_Horse' which is not a pattern and does not otherwise exist".
- Esse aviso é da mesma causa: ordem de carregamento (Groups validados antes dos Roles do mod).

---

## 5. Resumo e opções

| Aspecto | Conclusão |
|--------|-----------|
| **Regra (role)** | Define comportamento; precisa existir no registro antes de ser usado no spawn. |
| **Spawn (WorldNPCSpawn)** | Cada entrada tem um único `Id` = um role ID. Dois roles = duas entradas. |
| **Erro "does not exist as a role"** | A validação do spawn (e dos Groups) roda antes do role do mod estar registrado. |
| **Ordem de assets** | Definida pelo jogo (loadsAfter/loadsBefore nos stores); mod não controla. |

**Opções para o HorseFollow:**

1. **Não referenciar** Black_Horse em Group nem em Spawn no mod: usar só os do vanilla e sobrescrever apenas os JSONs de role. O log fica limpo; Black_Horse não spawne nas planícies pelo spawn de zona.
2. **Manter** Group e Spawn que referenciam Black_Horse e **aceitar** o SEVERE e os WARNs no log. Em jogo, bind/follow do Black_Horse podem continuar funcionando (o role existe depois que tudo carrega); só a validação na carga que falha.
3. **Remover só Black_Horse** do spawn (deixar só Horse) e do grupo Horse: elimina o SEVERE e o aviso do spawn; o aviso do Tag Set pode continuar se o grupo Horse ainda referenciar Black_Horse.
