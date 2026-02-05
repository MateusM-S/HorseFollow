# Relatório: Erro Template_Animal_Tamed.json

## Resumo da linha do tempo

1. **Erro inicial**: `Computable references must define a list of 'Interfaces'` no Template_Animal_Tamed
2. **Tentativa 1**: Adicionado `Interfaces: [ "Hytale.Instruction.Null" ]` ao Reference com Compute
3. **Resultado**: Cavalo sumiu ao domar; Horse_Friendly e Ram_Friendly falharam com `Component code null does not match any of slot codes: [Hytale.Instruction.Null]`
4. **Correção final**: Revertido para referência estática `"Reference": "Component_Instruction_Wild_Sleep_State"` (padrão vanilla)

---

## Erro exato #1 (antes da tentativa de fix)

```
[NPC|P] FAIL: /Server/NPC/Roles/_Core/Templates/Template_Animal_Tamed.json: 
java.lang.IllegalStateException: Computable references must define a list of 'Interfaces' to control which components can be attached.
```

## Erro exato #2 (após adicionar Interfaces: Hytale.Instruction.Null)

```
[NPC|P] FAIL: /Server/NPC/Roles/Creature/Livestock/Ram_Friendly.json: 
java.lang.IllegalStateException: Component code null does not match any of slot codes: [Hytale.Instruction.Null].

[NPC|P] FAIL: /Server/NPC/Roles/Creature/Livestock/Horse_Friendly.json: 
java.lang.IllegalStateException: Component code null does not match any of slot codes: [Hytale.Instruction.Null].
```

Quando o jogo tentava trocar para Horse_Friendly ao domar:
```
[RoleChangeSystem] Failed to change role: java.lang.RuntimeException: Cannot use role template 'Horse_Friendly' (905): 
java.lang.IllegalStateException: Builder Horse_Friendly failed validation!
```

## Causa

- Os componentes `Component_Instruction_Wild_Sleep_State` e `HF_Component_Instruction_Stay_Sleep_State` **não implementam** `Hytale.Instruction.Null`. O engine valida o "Component code" ao resolver a referência; o resultado é "null" (não registrado como Instruction.Null), gerando a falha.

## Correção final aplicada

Revertido para referência estática igual ao vanilla:

```json
"Reference": "Component_Instruction_Wild_Sleep_State",
```

Sem Compute, sem Interfaces. Horse_Friendly e Ram_Friendly voltam a funcionar.

## Impacto no Stay (deitar ao ficar)

O parâmetro `SleepInstruction` com Compute permitiria que o role Stay usasse `HF_Component_Instruction_Stay_Sleep_State`. Com a reversão, isso não é mais possível via override. Para implementar "deitar ao stay", será necessário outra estratégia (ex.: role Horse_Friendly_Stay com bloco de instruções duplicado e Reference estático para HF_Component_Instruction_Stay_Sleep_State).

---

*Atualizado em 02/02/2025 após análise do log pós-fix.*
