# Informações sobre ParticleColor / Color em Partículas

## O que é `ParticleColor` / `Color`

`ParticleColor` ou `Color` é um campo usado em **efeitos de partículas** do Hytale para definir a **cor** das partículas usando código hexadecimal.

## Formato

O valor `"#cabc3f"` é uma **cor hexadecimal** que representa:
- **#cabc3f** = Amarelo/Dourado claro
- Formato: `#RRGGBB` (Red, Green, Blue em hexadecimal)

## Como Usar

### Estrutura em Interações

Dentro de `Effects`, você pode usar `Particles` (array) ou `WorldParticles` (array), e cada objeto de partícula pode ter um campo `Color`:

```json
{
  "Effects": {
    "Particles": [
      {
        "SystemId": "Nome_do_Sistema_Particulas",
        "Color": "#cabc3f"
      }
    ]
  }
}
```

### Exemplo Real (do Rekindle_Embers)

```json
{
  "Type": "LaunchProjectile",
  "Effects": {
    "Particles": [
      {
        "SystemId": "GreenOrbTrail",
        "TargetEntityPart": "Entity",
        "TargetNodeName": "L-Hand",
        "Color": "#679d43"  // ← Cor verde
      },
      {
        "SystemId": "GreenOrbTrail",
        "TargetEntityPart": "PrimaryItem",
        "Color": "#679d43",  // ← Cor verde
        "TargetNodeName": "Handle"
      }
    ]
  }
}
```

### Exemplo com WorldParticles

```json
{
  "Effects": {
    "WorldParticles": [
      {
        "SystemId": "Impact_Sword_Basic"
        // Sem Color = usa cor padrão do sistema de partículas
      },
      {
        "SystemId": "Custom_Effect",
        "Color": "#cabc3f"  // ← Cor customizada (amarelo/dourado)
      }
    ]
  }
}
```

## Sobre a Cor `#cabc3f`

- **Hex**: `#cabc3f`
- **RGB**: R=202, G=188, B=63
- **Descrição**: Amarelo/Dourado claro, similar a cor de pão/trigo
- **Uso comum**: Efeitos de comida, consumo, alimentação

## Onde Usar

Você pode usar `Color` em:
- `Effects.Particles[]` - Partículas ligadas a entidades/itens
- `Effects.WorldParticles[]` - Partículas no mundo
- Dentro de interações que geram efeitos visuais

## Notas Importantes

1. **Nem todos os sistemas de partículas suportam Color** - Depende do `SystemId` usado
2. **Se não especificar Color**, usa a cor padrão do sistema de partículas
3. **O formato é sempre hexadecimal** com `#` no início
4. **Case-insensitive** - `#CABC3F` = `#cabc3f`

## Exemplo para Horse_Feed

Se você quiser adicionar partículas douradas quando consumir:

```json
{
  "Type": "ModifyInventory",
  "AdjustHeldItemQuantity": -1,
  "Next": {
    "Type": "Simple",
    "Effects": {
      "ItemAnimationId": "Interact",
      "WorldSoundEventId": "SFX_Items_Consume_Bread_Stereo_01",
      "WorldParticles": [
        {
          "SystemId": "Consume_Effect",
          "Color": "#cabc3f"  // ← Cor dourada/amarela
        }
      ]
    },
    "RunTime": 0.2
  }
}
```

**Nota:** Você precisaria ter um `SystemId` de partículas válido (ex: `"Consume_Effect"` ou algum sistema existente do Hytale que suporte colorização).

---

**Fim do Documento**
