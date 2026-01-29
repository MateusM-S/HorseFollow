# Guia: Charging com Secondary Input e Mensagem no Chat

## Resumo

Você pediu para habilitar `Secondary` input (botão direito) com `Charging` no item `Horse_Feed`, que após 2 segundos de segurar, envia uma mensagem no chat.

**Status:** ✅ Estrutura de interação criada  
**Limitação:** Enviar mensagem no chat via JSON não é suportado diretamente pelo Hytale

---

## 1. O que foi implementado

### 1.1) Arquivo de Interação Criado

**Arquivo:** `src/main/resources/Server/Item/Interactions/Horse_Feed_Charge.json`

Esta interação define:
- `Type: "Charging"` - Permite segurar o botão
- `Next["2.0"]` - Após 2 segundos, executa a ação
- `HorizontalSpeedMultiplier: 0.6` - Desacelera o jogador enquanto segura
- `CancelOnItemChange: true` - Cancela se trocar o item

### 1.2) Item Atualizado

**Arquivo:** `src/main/resources/Server/Item/Items/Horse_Feed.json`

Adicionado o bloco `Interactions` com `Secondary`:

```json
"Interactions": {
  "Secondary": {
    "Interactions": [
      {
        "Parent": "Horse_Feed_Charge"
      }
    ]
  }
}
```

---

## 2. Como Funciona

### 2.1) Fluxo de Interação

1. **Jogador segura botão direito** com `Horse_Feed` na mão
2. **Sistema inicia o charge** (pode mostrar animação/som se configurado)
3. **Se soltar antes de 2s**: Nada acontece (`Next["0"]`)
4. **Se segurar por 2s**: Executa a ação (`Next["2.0"]`)
5. **Se receber dano/trocar item**: Cancela (`Failed`)

### 2.2) Estrutura do Charging

```json
{
  "Type": "Charging",
  "Next": {
    "0": { /* soltou cedo */ },
    "2.0": { /* segurou 2 segundos */ }
  }
}
```

**Nota:** As chaves em `Next` são **strings** representando **segundos** (ex: `"2.0"` = 2 segundos).

---

## 3. Limitação: Mensagem no Chat

### 3.1) Por que não funciona via JSON?

O sistema de interações do Hytale (JSON) **não possui** um tipo de ação nativo para enviar mensagem no chat. As interações JSON são focadas em:
- Efeitos visuais (`WorldParticles`)
- Efeitos sonoros (`WorldSoundEventId`, `LocalSoundEventId`)
- Animações (`ItemAnimationId`)
- Modificações de inventário (`ModifyInventory`)
- Spawn de NPCs/blocos (`SpawnNPC`, `DestroyBlock`)
- Mas **NÃO** mensagens no chat

### 3.2) Soluções Possíveis

#### Opção A: Efeitos Visuais/Sonoros (JSON puro)

Você pode usar efeitos como feedback visual ao invés de mensagem no chat:

```json
{
  "Type": "Charging",
  "Next": {
    "2.0": {
      "Type": "Simple",
      "Effects": {
        "WorldSoundEventId": "SFX_Item_Use",
        "WorldParticles": [
          {
            "SystemId": "Effect_Success"
          }
        ]
      }
    }
  }
}
```

#### Opção B: Plugin Java (Recomendado para mensagem no chat)

Para enviar mensagem no chat, você precisa criar um **plugin Java** que escuta eventos de interação.

---

## 4. Implementação: Plugin Java para Mensagem no Chat

### 4.1) Estrutura Necessária

Você precisará:

1. **Criar um listener de eventos** que detecta quando a interação `Horse_Feed_Charge` é completada
2. **Enviar mensagem no chat** usando a API do Hytale

### 4.2) Exemplo de Código Java

**Arquivo sugerido:** `src/main/java/com/horsefollow/HorseFeedListener.java`

```java
package com.horsefollow;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.events.InteractionCompletedEvent;
import com.hypixel.hytale.server.core.universe.world.events.WorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.WorldEventListener;
import com.hypixel.hytale.server.core.universe.world.events.WorldEventManager;
import com.hypixel.hytale.server.core.universe.world.events.WorldEventType;
import com.hypixel.hytale.server.core.universe.message.Message;

public class HorseFeedListener implements WorldEventListener {

    private static final String HORSE_FEED_CHARGE_INTERACTION = "Horse_Feed_Charge";

    @Override
    public void onEvent(WorldEvent event) {
        if (event.getType() == WorldEventType.INTERACTION_COMPLETED) {
            InteractionCompletedEvent interactionEvent = (InteractionCompletedEvent) event;
            
            // Verifica se é a interação do Horse_Feed_Charge
            if (HORSE_FEED_CHARGE_INTERACTION.equals(interactionEvent.getInteractionId())) {
                Ref<EntityStore> playerRef = interactionEvent.getPlayerRef();
                if (playerRef != null && playerRef.isValid()) {
                    Store<EntityStore> store = playerRef.getStore();
                    
                    // Envia mensagem no chat
                    PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
                    if (player != null) {
                        String message = Localization.get(store, playerRef, "horsefollow.feed.charged");
                        player.sendMessage(Message.raw(message));
                    }
                }
            }
        }
    }

    @Override
    public WorldEventType[] getEventTypes() {
        return new WorldEventType[] { WorldEventType.INTERACTION_COMPLETED };
    }
}
```

**Nota:** Este código é um **exemplo conceitual**. A API real do Hytale pode variar. Você precisará verificar a documentação da API do Hytale para os eventos corretos.

### 4.3) Registrar o Listener

No `HorseFollowPlugin.java`, registre o listener:

```java
public class HorseFollowPlugin extends Plugin {
    // ...
    
    @Override
    public void onEnable() {
        // ... código existente ...
        
        // Registrar listener de interações
        WorldEventManager eventManager = getWorldEventManager();
        if (eventManager != null) {
            eventManager.registerListener(new HorseFeedListener());
        }
    }
}
```

### 4.4) Adicionar Tradução

**Arquivo:** `src/main/resources/Server/Languages/en-US/server.lang`

```
horsefollow.feed.charged=[HorseFollow] Feed charged! Ready to use.
```

**Arquivo:** `src/main/resources/Server/Languages/pt-BR/server.lang`

```
horsefollow.feed.charged=[HorseFollow] Ração carregada! Pronta para usar.
```

---

## 5. Alternativa Simples: Usar Efeitos Visuais

Se você não quiser criar código Java, pode usar efeitos visuais/sonoros como feedback:

### 5.1) Implementação Atual (com sons de consumo)

A interação já está implementada com efeitos sonoros similares ao consumo de comida:

```json
{
  "Type": "Charging",
  "FailOnDamage": true,
  "Effects": {
    "ItemAnimationId": "Consume",
    "LocalSoundEventId": "SFX_Consume",
    "ClearAnimationOnFinish": true,
    "ClearSoundEventOnFinish": true
  },
  "HorizontalSpeedMultiplier": 0.6,
  "CancelOnItemChange": true,
  "Next": {
    "0": { /* soltou cedo */ },
    "2.0": {
      "Type": "Simple",
      "Effects": {
        "WorldSoundEventId": "SFX_Consumed",
        "LocalSoundEventId": "SFX_Consumed"
      },
      "RunTime": 0.3
    }
  }
}
```

**Efeitos implementados:**
- ✅ **Animação "Consume"** - Player faz animação de comer durante o charge
- ✅ **Som durante o charge** (`SFX_Consume`) - Som de mastigação/comer
- ✅ **Som ao completar** (`SFX_Consumed`) - Som de conclusão do consumo
- ✅ **Desaceleração** - Player se move mais devagar enquanto segura
- ✅ **Cancela ao receber dano** - `FailOnDamage: true`

**Vantagens:**
- ✅ Funciona apenas com JSON
- ✅ Feedback visual/sonoro claro
- ✅ Não precisa de código Java

**Desvantagens:**
- ❌ Não mostra mensagem no chat
- ❌ Depende de assets de som/partículas existentes

---

## 6. Testando

### 6.1) Como Testar

1. **Compile o mod:**
   ```powershell
   .\gradlew build
   ```

2. **Instale no Hytale:**
   ```powershell
   .\Scrpts\BuildAndInstallToHytale.ps1
   ```

3. **No jogo:**
   - Pegue o item `Horse_Feed`
   - **Segure o botão direito do mouse** por 2 segundos
   - Deve executar a ação configurada

### 6.2) Debug

Se não funcionar:
- Verifique se o arquivo `Horse_Feed_Charge.json` está em `Server/Item/Interactions/`
- Verifique se o item tem o bloco `Interactions.Secondary` configurado
- Verifique os logs do servidor para erros de interação

---

## 7. Resumo das Opções

| Opção | Complexidade | Mensagem no Chat | Requer Java |
|-------|--------------|------------------|-------------|
| **Efeitos Visuais/Sonoros** | Baixa | ❌ Não | ❌ Não |
| **Plugin Java** | Média-Alta | ✅ Sim | ✅ Sim |

---

## 8. Próximos Passos

1. **Teste a interação básica** (sem mensagem no chat)
2. **Decida se quer mensagem no chat** ou se efeitos visuais são suficientes
3. **Se quiser mensagem no chat:**
   - Pesquise a API de eventos do Hytale
   - Implemente o listener Java
   - Adicione traduções
4. **Se quiser apenas efeitos:**
   - Configure `WorldSoundEventId` e `WorldParticles` no JSON
   - Teste e ajuste

---

**Fim do Guia**
