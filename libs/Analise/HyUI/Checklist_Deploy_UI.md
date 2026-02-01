# Checklist: implantar e testar a UI HyUI no Hytale

Se **nada mudou** in-game ao usar `/horsefollow config` ou `/horsefollow ui`:

## 1. Rebuild e instalação

1. **Build e instalar no Hytale**
   - Execute: `Scripts\BuildAndInstallToHytale.ps1`
   - Isso faz `gradlew clean build` e copia o JAR para:
     - `C:\Users\mateu\AppData\Roaming\Hytale\UserData\Mods\horsefollow.jar`

2. **Fechar o Hytale** antes de copiar o JAR (evita cache do mod antigo).

3. **Abrir o Hytale** de novo e entrar no mundo.

## 2. Teste no jogo

1. No jogo, digite: `/horsefollow config` ou `/horsefollow ui`.

2. **Se aparecer no chat:** `[HorseFollow] Abrindo configurações (HyUI)...`
   - O **novo código** está rodando.
   - Se a tela continuar vazia ou igual à antiga, o problema é **HyUI/renderização** (API, HTML ou layout), não implantação.

3. **Se NÃO aparecer** essa mensagem:
   - O JAR em uso é **antigo** ou o comando está falhando antes de abrir a UI.
   - Confirme que em Mods existe apenas `horsefollow.jar` (ou o HorseFollow-*.jar mais recente).
   - Veja o log do servidor (ex.: `libs/Log/latest_server.log` após `Scripts\CopyLatestServerLog.ps1`) para erros ao executar o comando.

## 3. Verificar conteúdo do JAR

Para garantir que o HTML e o HyUI estão no JAR:

```powershell
# Na raiz do projeto, após build
jar tf build\libs\HorseFollow-*.jar | findstr -i "ConfigPage_HyUI HyUI"
```

Deve listar entradas como `Common/UI/Custom/Pages/ConfigPage_HyUI.html` e classes/pacotes do HyUI.

## 4. Cache do Hytale

Se mesmo assim nada mudar:

- Feche o Hytale.
- Opcional: renomeie ou limpe a pasta de cache do Hytale (localização depende da instalação; às vezes em `AppData\Roaming\Hytale` ou na pasta do jogo).
- Abra de novo e teste outra vez.
