# Cleitin-O-BomDeGuerra — Relatório técnico

> **Nota:** medições feitas antes da migração para Maven, com os scripts PowerShell do repositório original (`Rob-DeCombate`, privado) e uma instalação local do Robocode 1.11.1 no Windows. Os números são de execuções reais daquela época; para reproduzir parte deles hoje use `mvn test -Pbenchmark` (veja o README).


## 1. Ambiente identificado (FASE 1)

| Item | Valor encontrado |
|---|---|
| Produto | **Robocode clássico 1.11.1** (`C:\robocode`, `robocode.core-1.11.1.jar`). Não é Tank Royale. |
| API usada | `robocode.AdvancedRobot` (pacote `robocode.*`), ângulos em radianos nos métodos `*Radians`. |
| Java | JRE **1.8.0_503** (o ReadMe indica Java 8 como alvo). O compilador ECJ embutido exige Java 17+, então o `build.ps1` usa um `javac 17.0.15` com `--release 8` → bytecode Java 8. |
| Execução | `robocode.bat` → `robocode.Robocode`; batalhas headless via `robocode.control.RobocodeEngine`. |
| Física (clássico) | Velocidade máx. 8 px/tick; aceleração 1, desaceleração 2; giro do corpo `10 − 0,75·|v|` °/tick, canhão 20 °/tick, radar 45 °/tick; bala `20 − 3p`; dano `4p (+2(p−1) se p>1)`; ganho de energia ao acertar `3p`; aquecimento do canhão `1 + p/5`, resfriamento 0,1/tick; dano de parede `|v|/2 − 1`; colisão 0,6. |
| Regras da competição | **Não informadas.** Premissas adotadas: sem threads/rede/reflexão/bibliotecas externas/arquivos; estado `static` só para aprendizado entre rounds. Formato (1v1 ou melee) desconhecido → o robô trata os dois. |

Verificações feitas no ambiente: `javap` sobre `robocode.jar` (existência de `setFireBullet`, `onRoundEnded`, `onBattleEnded`, etc.), listagem do repositório de robôs via `RobocodeEngine.getLocalRepository()`.

## 2. Arquitetura (FASE 2)

Arquitetura modular em um único pacote `cleitin`; todo o raciocínio roda no laço principal (um passo por tick). Os eventos só atualizam dados.

| Componente pedido | Classe | Responsabilidade |
|---|---|---|
| CombatController | `Cleitin-O-BomDeGuerra` | laço principal, eventos, escolha de alvo |
| MovementSystem | `MovementSystem` | wave surfing (1v1), risco mínimo (melee), detecção de tiros inimigos |
| TargetTracking + EnemyModel | `Enemy` | estado observado, extrapolação, estatísticas persistentes por inimigo |
| RadarSystem | `RadarSystem` | lock estreito (1v1) / varredura (melee) |
| GunSystem | `GunSystem` (+ `MyWave`, `Gf`) | 6 miras virtuais, ondas próprias, disparo |
| EnergyManager | `EnergyManager` | potência de tiro |
| BattleStatistics | `BattleStatistics` | métricas por round e acumuladas |

Justificativa: sem threads (proibido/arriscado) e sem estado global além de `Cleitin-O-BomDeGuerra.DB`/`Cleitin-O-BomDeGuerra.STATS`. Cada módulo é testável isoladamente e os parâmetros ficam em `Config`.

## 3. Algoritmos

### 3.1 Radar
- **1v1:** lock estreito: gira para o azimute extrapolado do inimigo mais um sobre-giro `atan(30/d)`; se o dado envelhece >3 ticks, varre.
- **melee:** varredura contínua; se o dado do alvo tem ≥3 ticks, o radar vai até ele e depois retoma a varredura no sentido do inimigo mais antigo. Garante dados de todos ≤ ~8–12 ticks.
- Corpo, canhão e radar são independentes (`setAdjust*ForRobotTurn/GunTurn`).

### 3.2 Mira (GunSystem)
Cinco miras virtuais calculadas a cada tick para o instante de disparo (o tiro sai com a direção do canhão **antes** do giro do tick; por isso a mira é calculada no tick anterior e só se dispara com o canhão já alinhado):
1. **head-on**; 2. **linear** (iterativa com tempo de voo e limites do campo); 3. **circular** (taxa de giro estimada); 4. **GuessFactor segmentado** (distância×velocidade lateral×aceleração×espaço até a parede = 180 segmentos × 31 bins, misturado com o histograma grosso conforme a confiança); 5. **GuessFactor grosso** (9 segmentos).

Toda onda (tiros reais e "ondas de aprendizado" a cada 2 ticks) guarda o ângulo de **todas** as miras. Quando a onda alcança o alvo: pontua-se cada mira (acertaria? meia-largura `atan(20/d)`), atualizam-se os histogramas GF com núcleo gaussiano e decaimento. A mira usada é a de melhor taxa de acerto (decaimento 0,965/onda) contra aquele alvo, com prior fraco e desempate a favor das miras aprendidas — a escolha é adaptativa e estatística, não fixa. Dados envelhecidos (>3 ticks) não disparam.

### 3.3 Movimento 1v1 — Wave Surfing
- **Detecção de tiros (estimativa):** queda de energia `prev − atual − dano que nós causamos + ganho por acertar-nos ∈ [0,1; 3,0]`, ignorando colisão com parede (`|v|/2−1`). Gera uma `EnemyWave` com origem na posição do tick anterior. **São estimativas**, e não confirmações — o `matched` do relatório mede quantos acertos reais casaram com uma onda.
- **Avaliação:** para as 2 ondas mais próximas simula-se a órbita em cada sentido com a **mesma física do robô real** (giro limitado, aceleração/desaceleração, *wall smoothing*) até o impacto; o perigo no GuessFactor previsto vem de histogramas por inimigo: acertos reais (peso alto), "visitas" (onde estávamos quando a onda passou — comportamento de *flattener*) e um prior (tiro direto / linear). Escolhe-se o sentido de menor perigo, com histerese de 4%.
- **Sem ondas:** órbita com inversões aleatórias controladas (12–52 ticks).
- **Distância:** 430 px (260 se o inimigo não atira; 110 se desabilitado).

### 3.4 Movimento melee — risco mínimo
Candidatos em 24 direções × 3 raios; risco = Σ inimigos `(energia+30)/d² · (1+|cos|)` + penalidade de proximidade (<160 px) + paredes/cantos + ondas inimigas estimadas (peso reduzido: podem mirar em outro) + atração suave pela distância preferida do alvo (360 px); penaliza viradas bruscas. Reavalia a cada 7 ticks, ao chegar ou se travar.

### 3.5 Energia
Potência `(900−d)/270` limitada a [0,1; 3]; alvo parado/próximo → ≥2,4; taxa de acerto <7% (≥12 tiros) → −25%, >30% → +15%; melee ≤2,2; tetos por energia restante (50/30/15/8/4); tiro de finalização com a potência mínima que mata; inimigo desabilitado → 0,1; nunca atira com energia <0,2 (não se desabilita).

### 3.6 Alvo (melee)
Pontuação: proximidade, fragilidade, finalização possível, facilidade de acerto (taxa das miras), alvo quase parado, ameaça, histerese (+0,25 ao alvo atual) e penalização de dado velho.

## 4. Decisões e limitações
- **Dados confirmados** (tiros reais, acertos, dano, posição escaneada) vs **estimativas** (tiros inimigos por queda de energia, potência dos tiros) estão separados no código e nos nomes (`EnemyWave`, `shotsDetected`).
- **Limitação:** não há bullet shadows, nem previsão do tipo de mira do inimigo (só o histograma de acertos); em melee as ondas inimigas assumem mira em nós.
- **Limitação de teste:** só havia robôs de exemplo do Robocode e robôs de teste escritos aqui; **nenhum robô de ranking (RoboRumble)** estava disponível, então o desempenho contra adversários de alto nível é desconhecido.
- **CPU:** ~25 µs/tick em média, pico ≈ 0,7 ms (melee de 15) e 2,6 ms no primeiro tick (carga de classes) — bem abaixo do limite do Robocode.

## 5. Melhorias futuras
KNN/dynamic clustering para a mira, bullet shadows no surf, surf de mais ondas, "flattener" ativável por detecção de aprendiz, modelo de mira do inimigo (head-on/linear/GF) para ponderar o prior, jogo de equipe.
