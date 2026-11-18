# Análise comparativa — Diamond × DefMode × Reimu × Cleitin-O-BomDeGuerra

> **Nota:** medições feitas antes da migração para Maven, com os scripts PowerShell do repositório original (`Rob-DeCombate`, privado) e uma instalação local do Robocode 1.11.1 no Windows. Os números são de execuções reais daquela época; para reproduzir parte deles hoje use `mvn test -Pbenchmark` (veja o README).


Plataforma: **Robocode clássico 1.11.1 (Java 8)** — as três referências usam a mesma API (`robocode.*`), então não há mistura com Tank Royale.
Método: leitura do código-fonte baixado em pasta isolada (Diamond: ~60 arquivos de produção; Reimu: `Reimu.java` + README; DefMode: gist completo), compilação com `javac --release 8` e execução como adversários reais. **Nada foi copiado**: as técnicas foram reimplementadas com código próprio. Licenças: Diamond usa zlib modificada, Reimu usa MIT, DefMode não declara licença (usado só localmente como adversário de teste, não está no repositório).

## 1. O que cada referência faz (evidências no código)

**Diamond (Voidious)** — robô de topo do RoboRumble (README: #2 em 1v1 e melee em ago/2015; perdia só para o DrussGT).
- *Movimento 1v1:* Wave Surfing com 3 opções por onda (horário, anti-horário, **parar**) e **recursão** sobre as ondas seguintes (`SurfMover`); perigo = taxa de acerto do inimigo × dano × 1/tempo-de-impacto × fator de aproximação; **bullet shadows**; interseção **precisa** da janela de acerto (`Wave.preciseIntersection`) e ângulo de fuga **preciso** (simula o movimento até a parede). Distância-alvo ≈ 650.
- *Dados de movimento:* 11 "visões" KNN (`MoveEnemy`): visões "recentes" (K=1..100, decaimento 1,8) registrando **acertos**, e 3 visões "achatadoras" registrando **visitas**, habilitadas só quando a taxa de acerto do inimigo passa de um limiar (`KnnView`). 9 dimensões com pesos (`NormalFormula`: tempo de voo, velocidade com sinal, seno/cosseno do rumo relativo, aceleração, distância à parede à frente e atrás, tempo desde a mudança de velocidade).
- *Mira 1v1:* `TripHammerKnnGun` (KNN + densidade tricúbica sobre 59 ângulos, largura de banda = 2× largura angular do alvo, pesos de dimensão que mudam com o tamanho dos dados) e **`AntiSurferGun`** (4 visões de 125/400/1500/4000 amostras, K=3 cada, kernel gaussiano). Seleção por **mira virtual** com pontuação gradual (`VirtualGunsManager`: crédito decai exponencialmente com o erro angular, normalizado pela largura do alvo e pelo intervalo de fuga; sem decaimento no tempo).
- *Melee:* `MeleeMover` (risco mínimo: 64 candidatos, energia/razão, perpendicularidade, **amortecimento por multidão**, termo anti-padrão com pontos aleatórios recentes, bônus 0,8 ao destino anterior, simulação de 5 ticks para viabilidade de parede) e `MeleeGun` (vetores de deslocamento; um ângulo que acerta vários inimigos).
- *Radar:* trava em 1v1 com sobre-giro de 22,5°; em melee varre em direção ao inimigo mais "velho".
- *Potência:* 1,95 base (2,95 a <150 px), reduz com pouca energia, limitada a energia_inimigo/4; melee 2,999→0,999 conforme nº de inimigos/distância.

**DefMode (Roger Oba)** — ~20 linhas: gira a arma continuamente, vira o corpo para o inimigo, anda 100 à frente e atira com potência 3 sempre que escaneia. Declara 98,29% de vitórias em 10.000 batalhas **contra `sample.Walls`** (um único adversário simples). Fraquezas: movimento previsível (investida em linha reta), nenhuma mira preditiva, gasto de energia máximo (potência 3 fixa), nada de evasão. Reproduzi: contra o meu robô ganhou 3 de 100 rounds.

**Reimu (g-otn)** — "MegaBot" simples (2º lugar no 6º Robocode do Robótica Paula Souza, 2018). Órbita com ângulo de 40°/10° dependendo da distância, **inverte o sentido ao detectar queda de energia (0,1–3,0, excluindo ~0,6 de colisão)**, mira linear com amortecimento (×0,85; ×0,5 a <120 px), troca de alvo por energia/distância/quem atirou, potência por distância (3 a ≤300 px; `1,1+300/d` além). README declara a proteção de parede como "quebrada" — o código tem um ramo morto (<60 px). Fraquezas: reversão previsível, falsos positivos de tiro, radar estreito em melee.

## 2. Comparação técnica

| Critério | Diamond | DefMode | Reimu | Cleitin (1.3 → 1.4) |
|---|---|---|---|---|
| Arquitetura | módulos separados, ~60 classes, k-d tree | 1 classe | 1 classe | 11 classes, sem dependências |
| Movimento 1v1 | surf 3 opções + recursão + sombras | investida | órbita + reverter ao tiro | surf 2 opções (+ parar/recursão opcionais) |
| Mira | KNN multi-janela + KDE, 2 guns virtuais | head-on implícito | linear amortecida | 8 miras virtuais (head-on, linear, circular, 3 GF, KNN, KDE) |
| Previsão de posição | deslocamento/KNN | nenhuma | linear | iterativa com paredes (linear/circular) + KNN |
| Evasão de tiros | sim (principal ponto forte) | não | reverte sentido | sim (KNN aprendido) |
| Radar | lock + varredura do mais velho | giro da arma | lock/arco | lock 1v1; arco em melee |
| Energia | regras por distância/energia/nº de inimigos | fixa 3 | por distância | por distância/energia/precisão/finalização |
| Seleção de alvo | mais próximo (melee) | qualquer escaneado | energia/distância/quem atirou | pontuação multi-fator |
| Adaptação | gating por taxa de acerto, flattener | nenhuma | nenhuma | placar de miras, KNN do desvio aprendido por inimigo |
| Custo por tick | alto (k-d tree) | mínimo | mínimo | 50–90 µs |
| Robustez | testes unitários | trivial | parede quebrada | sem turnos pulados medidos |

Técnicas — **encontradas no código** (E) × **sugestão nova de engenharia** (N):
| Técnica | Origem | Estado no Cleitin | Ganho medido |
|---|---|---|---|
| Surf com opção "parar" + recursão (E, Diamond) | `SurfMover` | implementada (`SURF_STOP`, `SURF_RECURSE`) | nenhum contra o Diamond (13–17 vitórias de 100 em todas as variantes) |
| KNN multi-janela + KDE gaussiano para mira (E, Diamond) | `AntiSurferGun` | implementada (gun `kde`) | nenhum (precisão 8% com/sem) |
| Desvio imitando mira KNN multi-janela (N, a partir do E) | — | implementada (`SURF_MULTI`) | pequeno (Diamond acerta 11,5% vs 12,3%; pontos 33% vs 29% em 1 teste, 28,7% na validação final) |
| Pontuação gradual das miras virtuais (E) | `VirtualGunsManager` | **não implementada** (seria o próximo passo) | — |
| Amortecimento por multidão / bônus ao destino / razão de energia no melee (E, Diamond) | `MeleeMover` | implementadas, **desligadas** | média 113 vs base 120 primeiros lugares → sem ganho |
| Bullet shadows (E) | `Wave` | não implementada | — |
| Interseção/ângulo de fuga precisos (E) | `Wave` | não implementada | — |
| Reversão de sentido ao detectar tiro (E, Reimu) | `Reimu` | já coberta pelo surf (que decide com base no perigo) | — |
| Radar em arco no melee (existia em Reimu) | `Reimu` | implementado antes (`RADAR_ARC`) | leve |

## 3. Diagnóstico do robô original (v1.3) e correções

| Problema | Impacto | Solução / resultado |
|---|---|---|
| Contra o Diamond: 15/100 vitórias, precisão 7%, leva 12% de acertos | perde para robôs de elite | testadas 5 intervenções (tabela acima); **nenhuma moveu o resultado** → limite do desenho atual |
| Seleção de mira alternava entre todas as miras (inclusive head-on/linear) contra o Diamond | mira instável | adicionadas KNN e KDE; seleção continua por placar (sem efeito mensurável) |
| Desvio testado só contra bots com mira simples | superestimava a defesa | agora o benchmark inclui o Diamond real |
| Variância alta dos testes de melee (±8 vitórias em 60) | decisões baseadas em ruído | procedimento: medir a base 2× e testar cada mudança isolada |
| Nenhum erro de compilação, exceção ou turno pulado (`skipped=0`) | — | mantido |

## 4. Arquitetura final
Controlador (`Cleitin_O_BomDeGuerra`) → `RadarSystem`, `MovementSystem` (surf KNN 1v1 / risco mínimo melee), `GunSystem` (8 miras virtuais + `Knn`/`Gf`), `EnergyManager`, `Enemy` (modelo persistente por adversário), `BattleStatistics`. Parâmetros em `Config` (todas as novas técnicas têm chave para ligar/desligar e comparar A/B).

## 5. Limites conhecidos e próximos passos
- Contra o Diamond real o robô perde (~12–18 de 100 rounds): o ganho exigiria bullet shadows, interseção precisa e visões com gating por taxa de acerto — mudanças de vários dias e com ganho incerto.
- Pontuação gradual das miras virtuais e janelas "recentes" K=1 no desvio são os próximos experimentos mais baratos.
- Melee contra o Diamond (cenário D5): 4–12 primeiros lugares em 60; o Diamond domina melee.
- Resultados valem para estes adversários e a máquina de teste; a variância é alta (±5 pontos percentuais por 100 rounds).
