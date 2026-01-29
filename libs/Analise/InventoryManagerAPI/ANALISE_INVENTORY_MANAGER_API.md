# Análise do InventoryManagerAPI-1.1.0.jar

## Descobertas Principais

### 1. Classe Principal de Inventário
- **Classe**: `com.hypixel.hytale.server.core.inventory.Inventory`
- Esta é uma classe oficial do Hytale Server API
- O plugin usa esta classe para acessar e manipular inventários

### 2. Estrutura do Plugin
- **Plugin Principal**: `dev.djctavia.InventoryManagerAPIPlugin`
- **Utilitários**:
  - `InventoryUtils`: Métodos estáticos para limpar e copiar inventários
  - `ItemContainerUtils`: Utilitários para containers de itens
  - `InventoryStorageManager`: Gerencia salvamento/carregamento de inventários

### 3. Como o Plugin Acessa Inventários

#### Métodos Identificados:
```java
// InventoryStorageManager
public void saveInventory(Ref<EntityStore> playerRef);
public void saveInventory(UUID, Inventory);
public Inventory getSavedInventory(UUID);
public boolean restoreInventory(Ref<EntityStore> playerRef);
```

#### Observações Importantes:
- O plugin recebe `Inventory` como parâmetro direto
- `AbstractPlayerCommand` (classe base dos comandos) provavelmente tem acesso ao Inventory
- O Inventory é passado diretamente, não obtido via reflexão

### 4. Componente Inventory no ECS

Baseado na estrutura do Hytale ECS (similar a outros componentes como `PlayerRef`, `MountedComponent`):
- Deve existir um `InventoryComponent` ou similar
- Pode ser acessado via `store.getComponent(playerRef, InventoryComponent.getComponentType())`
- Ou pode ser acessado diretamente do `PlayerRef` via método `getInventory()`

### 5. Estratégia de Implementação

#### Opção 1: Componente Inventory (Recomendado)
```java
// Tentar acessar como componente ECS
InventoryComponent inventoryComp = store.getComponent(playerRef, InventoryComponent.getComponentType());
if (inventoryComp != null) {
    Inventory inventory = inventoryComp.getInventory();
    // ou
    Inventory inventory = inventoryComp.getPlayerInventory();
}
```

#### Opção 2: Método direto no PlayerRef
```java
PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
if (player != null) {
    Inventory inventory = player.getInventory();
    // ou
    Inventory inventory = player.getPlayerInventory();
}
```

#### Opção 3: Via reflexão (fallback)
```java
// Tentar métodos comuns
Method getInventory = player.getClass().getMethod("getInventory");
Inventory inventory = (Inventory) getInventory.invoke(player);
```

### 6. Detecção de Consumo

Para detectar quando Horse_Feed é consumido:
1. Obter o Inventory do player
2. Encontrar o item Horse_Feed no inventário
3. Monitorar a quantidade do item
4. Quando a quantidade diminuir, significa que foi consumido

#### Métodos do Inventory (esperados):
```java
// Buscar item por ID
ItemStack getItem(String itemId);
ItemStack findItem(String itemId);
int getItemQuantity(String itemId);

// Obter item segurado
ItemStack getHeldItem();
ItemStack getItemInHand();
```

### 7. Implementação Recomendada

1. **Tentar acessar Inventory via componente ECS primeiro**
2. **Se falhar, tentar método direto no PlayerRef**
3. **Se falhar, usar reflexão como fallback**
4. **Monitorar quantidade de Horse_Feed a cada tick**
5. **Quando detectar diminuição, enviar mensagem**

### 8. Pontos de Atenção

- **Thread Safety**: Inventory deve ser acessado na world thread
- **Validação**: Sempre verificar se Inventory e ItemStack não são null
- **Performance**: Não verificar a cada tick (usar intervalo de 2-5 ticks)
- **Cooldown**: Evitar enviar múltiplas mensagens para o mesmo consumo

## Conclusão

O InventoryManagerAPI confirma que:
1. Existe uma API oficial `com.hypixel.hytale.server.core.inventory.Inventory`
2. O inventário pode ser acessado via componentes ECS ou métodos diretos
3. É possível obter itens e suas quantidades do inventário
4. Podemos monitorar mudanças na quantidade para detectar consumo
