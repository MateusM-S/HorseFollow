# Todas as animações referentes à tocha

## 1. Animações do **item** (jogador segurando a tocha)

Conjunto: **Server/Item/Animations/Torch.json**  
Caminho base dos arquivos: **Common/Characters/Animations/Items/Off_Handed/Torch/**

Cada entrada é um **ID** usado pelo engine; dentro há ThirdPerson, FirstPerson e às vezes ThirdPersonMoving (arquivos `.blockyanim`).

| ID no JSON | Descrição | Arquivos .blockyanim (ThirdPerson / FirstPerson) |
|------------|-----------|--------------------------------------------------|
| **Idle** | Parado segurando a tocha | Idle.blockyanim / Idle_FPS.blockyanim |
| **Walk** | Andando | Walk / Walk_FPS |
| **WalkBackward** | Andando para trás | Walk_Backward / Walk_Backward_FPS |
| **Run** | Correndo | Run / Run_FPS |
| **RunBackward** | Correndo para trás | Run_Backward / Run_Backward_FPS |
| **Sprint** | Sprintando | Sprint / Sprint_FPS |
| **Crouch** | Agachado | Crouch / Crouch_FPS |
| **CrouchWalk** | Andando agachado | Crouch_Walk / Crouch_Walk_FPS |
| **CrouchWalkBackward** | Andando agachado para trás | Crouch_Walk_Backward / Crouch_Walk_Backward_FPS |
| **Jump** | Pulando | Jump (só ThirdPerson) |
| **JumpWalk** | Pulando (andando) | Jump_Far |
| **JumpRun** | Pulando (correndo) | Jump_Far |
| **Fall** | Caindo | Fall |
| **FallFar** | Caindo de longe | Fall_Far |
| **FlyIdle** | Voando parado | Fly/Fly_Idle / Fly/Fly_Idle_FPS |
| **Fly** | Voando | Fly/Fly / Fly/Fly_FPS |
| **FlyBackward** | Voando para trás | Fly/Fly_Backward / Fly/Fly_Backward_FPS |
| **FlyFast** | Voando rápido | Fly/Fly_Fast / Fly/Fly_Fast_FPS |
| **SwimIdle** | Nadando parado | Swim/Swim_Idle / Swim/Swim_Idle_FPS |
| **Swim** | Nadando | Swim/Swim / Swim/Swim_FPS |
| **SwimBackward** | Nadando para trás | Swim/Swim_Backward / Swim/Swim_Backward_FPS |
| **SwimFast** | Nadando rápido | Swim/Swim_Fast / Swim/Swim_Fast_FPS |
| **SwimJump** | Pulando na água | Swim/Swim_Jump / Swim/Swim_Jump_FPS |
| **SwimFloat** | Flutuando na água | Swim/Swim_Float / Swim/Swim_Float_FPS |
| **SwimSink** | Afundando | Swim/Swim_Sink / Swim/Swim_Sink_FPS |
| **SwimDive** | Mergulhando | Swim/Swim_Dive / Swim/Swim_Dive_FPS |
| **SwimDiveBackward** | Mergulhando para trás | Swim/Swim_Dive_Backward / Swim/Swim_Dive_Backward_FPS |
| **SwimDiveFast** | Mergulho rápido | Swim/Swim_Dive_Fast / Swim/Swim_Dive_Fast_FPS |
| **FluidIdle** | Parado em fluido (água rasa) | Idle / Idle_FPS |
| **FluidWalk** | Andando em fluido | Walk / Walk_FPS |
| **FluidWalkBackward** | Andando para trás em fluido | Walk_Backward / Walk_Backward_FPS |
| **FluidRun** | Correndo em fluido | Run / Run_FPS |
| **ClimbIdle** | Escalando parado | Climb/Climb_Idle / Climb/Climb_Idle_FPS |
| **ClimbUp** | Escalando para cima | Climb/Climb_Up / Climb/Climb_Up_FPS |
| **ClimbDown** | Escalando para baixo | Climb/Climb_Down / Climb/Climb_Down_FPS |
| **ClimbLeft** | Escalando para a esquerda | Climb/Climb_Left / Climb/Climb_Left_FPS |
| **ClimbRight** | Escalando para a direita | Climb/Climb_Right / Climb/Climb_Right_FPS |
| **Build** | Construindo/colocando bloco | Attacks/Build/Build, Build_Moving / Build_FPS |
| **Interact** | Interagindo (uso secundário, ex. acender/apagar) | Interact / Interact_FPS |
| **SwingLeft** | Ataque balanço esquerda | Attacks/Swing_Left/Swing_Left, Swing_Left_Moving / Swing_Left_FPS |
| **SwingRight** | Ataque balanço direita | Attacks/Swing_Right/Swing_Right, Swing_Right_Moving / Swing_Right_FPS |

---

## 2. Animações do **modelo da tocha** (item/bloco em si)

Pasta: **Common/Items/Torch/**  
São animações do **modelo 3D** da tocha (chama acesa/apagada), não do personagem.

| Arquivo | Uso |
|---------|-----|
| **Torch_Burn.blockyanim** | Chama acesa (loop do modelo quando colocada acesa). |
| **Torch_Off.blockyanim** | Tocha apagada (estado "Off" do bloco Furniture_Crude_Torch). |

Referência no item: em **BlockType.State.Definitions.Off** o Furniture_Crude_Torch usa `"CustomModelAnimation": "Items/Torch/Torch_Off.blockyanim"`.

---

## 3. Resumo por categoria

- **Item (jogador):** 38 IDs no Torch.json (movimento, natação, escalada, voo, Build, Interact, SwingLeft, SwingRight).
- **Modelo da tocha:** 2 animações (Torch_Burn, Torch_Off) em Common/Items/Torch/.

Todos os `.blockyanim` do jogador estão em **Common/Characters/Animations/Items/Off_Handed/Torch/** (e subpastas Attacks, Climb, Fly, Swim).
