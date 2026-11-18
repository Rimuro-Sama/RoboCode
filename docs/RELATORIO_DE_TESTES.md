# Cleitin-O-BomDeGuerra — Relatório de testes (resultados reais)

> **Nota:** medições feitas antes da migração para Maven, com os scripts PowerShell do repositório original (`Rob-DeCombate`, privado) e uma instalação local do Robocode 1.11.1 no Windows. Os números são de execuções reais daquela época; para reproduzir parte deles hoje use `mvn test -Pbenchmark` (veja o README).


Metodologia: `Harness` headless (RobocodeEngine 1.11.1), `cleitin.Cleitin_O_BomDeGuerra` contra cada adversário, **35 rounds** por duelo (800×600) e 35 rounds por melee (1000×1000). Dados brutos: `docs/resultados-anteriores/final_all.txt`, `docs/resultados-anteriores/v1_*.txt`, `docs/resultados-anteriores/sweep_*.txt`. Ressalva: os adversários são robôs de exemplo do Robocode e 4 robôs de teste escritos aqui; **nenhum robô de ranking foi testado**.

## Duelos 1v1 (versão final)
| Adversário | Perfil | 1º lugar | % da pontuação |
|---|---|---|---|
| SittingDuck | parado | 35/35 | 100,0 |
| Circler (teste) | circular | 35/35 | 99,3 |
| Walls | parede/longa dist. | 35/35 | 99,2 |
| SpinBot | agressivo | 35/35 | 100,0 |
| Crazy | imprevisível | 35/35 | 99,8 |
| Strafer (teste) | imprevisível + mira linear | 35/35 | 99,0 |
| **GFBot (teste)** | **GuessFactor, o mais forte** | **33/35** | **87,3** |
| Camper (teste) | defensivo/canto | 35/35 | 99,5 |
| RamFire | curta distância | 35/35 | 97,3 |
| Tracker | seguidor | 35/35 | 100,0 |
| TrackFire | seguidor + tiro | 35/35 | 99,8 |
| Fire | parado/gira | 35/35 | 99,8 |
| Corners | cantos | 35/35 | 99,3 |
| VelociRobot | velocidade | 35/35 | 97,3 |

## Melee (35 rounds)
| Cenário | 1º lugar | % pontuação |
|---|---|---|
| 5 robôs (mix) | 35/35 | 47,6 |
| 10 robôs (mix) | 34/35 | 25,3 |
| 5 robôs "fortes" | 32/35 | 44,0 |
| 15 robôs (15 rounds, versão anterior) | 12/15 | 14,9 |

## Experimentos (A/B)
- **Ablation do surf** (vs GFBot, 80 rounds): com surf o GFBot acertou **7,0%** (226/3238) e perdemos quase nada (79/80); sem surf **20,5%** (701/3420) e 44/80 vitórias.
- **Casamento tiro↔onda:** 171/178 (96%) dos acertos sofridos casaram com uma onda detectada.
- **Distância de órbita** 330/430/530: GFBot acerta 8,7/7,6/7,3% — dentro do ruído; mantido 430 (agora escalonado pela arena).
- **Pesos do surf** (visitas 0/2,2/6; acertos 3/7/14; prior 0/1,6/4): diferenças dentro do ruído (7,2–8,4%); padrões mantidos.
- **Mira vs surfista (autojogo vs v1, 80 rounds):** onda virtual a cada 1/2/4 ticks → 48,7/51,7/49,1% de pontuação; `GF_DECAY` 0,98/0,992/0,998 → 52,3/52,1/40,7%. Padrões (2 e 0,992) mantidos; 0,998 é pior. Precisão contra um surfista: ~11–12%.
- **Arenas** 400², 1200², 600×1000, 1600×400 (GFBot, 40 rounds): 39, 40, 40, 37 vitórias.
- **CPU:** média ≈ 25 µs/tick; pico ≈ 0,7 ms (melee de 15); 2,6 ms no 1º tick (carga de classes). `onSkippedTurn` é contado (`skipped=` no log).

## Não testado
Robôs de ranking, equipes, arenas além das listadas, execução na GUI com TPS real, máquina mais lenta que esta.

## Adversários fortes escritos para esta rodada (`test\tst`, 50 rounds, 800×600)
| Adversário | Descrição | Cleitin-O-BomDeGuerra 1º lugar | % pontuação | Tiros do inimigo que nos acertam |
|---|---|---|---|---|
| SurfGF | wave surfing + mira GuessFactor segmentada | 29/50 | 58,0 | 258/2290 (11%) |
| Sniper | longa distância, surf leve, GF com potência baixa | 38/50 | 66,3 | 216/2042 (11%) |
| Charger | agressivo, potência 3, abalroa | 49/50 | 83,3 | 116/365 (32%) |
| DropDodger | esquiva por queda de energia + mira circular | 50/50 | 96,1 | 38/1253 (3%) |
| GravBot | anti-gravidade, bom em melee | 50/50 | 98,3 | 22/819 (3%) |

Melee de 1000×1000 com os cinco juntos (40 rounds): Cleitin-O-BomDeGuerra em 1º lugar em **39/40**, 43,8% da pontuação (DropDodger 16,7%, Sniper 12,1%, SurfGF 10,8%, GravBot 9,8%, Charger 6,9%).

Leitura: contra surfistas com mira aprendida o duelo é equilibrado-favorável (58–66%), não dominante; o ponto mais frágil é o curto alcance contra o Charger (32% dos tiros dele acertam, embora o Cleitin-O-BomDeGuerra ainda vença 49/50).

## Versão 1.1 (melhorias a partir dos testes com adversários fortes)
Medições A/B (150 rounds, a menos que indicado). Chaves no `Config`.
| Mudança | Resultado | Decisão |
|---|---|---|
| 3ª mira GuessFactor (segmentada por tempo desde a última inversão do inimigo) | SurfGF 66,5→68,5%, Sniper 63,6→63,2%, GFBot 90,2→89,6% (ruído) | mantida (inofensiva) |
| Teto de potência contra alvos "difíceis" (`HARD_POWER_CAP` 2,0 / 1,4) | SurfGF 65,3 / 61,9%, Sniper 62,8 / 60,5% (pior ou igual) | **desligada** (3,0) |
| Distância de órbita maior contra agressores de curto alcance (`CLOSE_SCALE` 1,0 / 1,3 / 1,6) | Charger: dano sofrido 5380 / 4534 / 3906; pontuação 80,5 / 82,9 / 85,5%; RamFire sem perda relevante | **adotada: 1,6** |
| `SURF_SECOND_WAVE` 0,15 / 0,9; `SURF_HYSTERESIS` 0 / 0,12 | acertos inimigos 10–11% em todas, vitórias 95–113/150 (ruído) | mantidos os padrões |

Regressão final da v1.1 (50 rounds): 1º lugar em 50/50 contra 16 dos 19 duelos; SurfGF 35/50 (65,8%), Sniper 37/50 (66,0%), GFBot 49/50 (88,4%); Charger 50/50 (87,1%, antes 81,6%). Melee: 50/50, 49/50, 45/50 e 49/50 em 1º lugar.

## Torneio aleatório com 6 adversários fortes (inclui `legiao_invicta`)
Script `tournament.ps1` (seed 7): 36 duelos de 20 rounds com adversário e arena sorteados (600² a 1200×900) + 8 melees de 20 rounds com 3–6 adversários sorteados (1000×1000). Dados: `docs/resultados-anteriores/torneio.txt`.

| Adversário | Partidas | Rounds ganhos | Pontos do Cleitin-O-BomDeGuerra |
|---|---|---|---|
| GravBot | 10 | 200/200 (100%) | 98% |
| DropDodger | 2 | 40/40 (100%) | 94% |
| Charger | 7 | 138/140 (99%) | 85% |
| Sniper | 6 | 85/120 (71%) | 64% |
| legiao_invicta | 5 | 67/100 (67%) | 64% |
| SurfGF | 6 | 78/120 (65%) | 62% |

Melees (parcela da pontuação do Cleitin-O-BomDeGuerra; justo seria 1/(N+1)): 31–60%, 1º lugar em 8–20 de 20 rounds; os piores casos são os melees com vários surfistas fortes juntos (melee 3: 32%, 8/20; melee 8: 33%, 9/20).

## Versão 1.2 — desvio de balas com KNN (imita a mira dos adversários fortes)
**Mudança principal:** o desvio passou a aprender, com 8 características da *nossa* situação no momento do tiro inimigo (velocidade lateral e de aproximação, aceleração, distância, espaço até a parede, tempo desde a última inversão, velocidade da bala), onde uma mira KNN inimiga nos acertaria — usando acertos reais (peso 3) e todas as ondas que passaram por nós (peso 1). O perigo combina esse KNN (`SURF_KNN_WEIGHT=10`, `SURF_KNN_K=24`) com os histogramas anteriores. No melee, o mesmo perigo aprendido passou a pontuar os destinos candidatos (`MELEE_WAVE_K`). Também foi adicionada uma 7ª mira (KNN de 8 características, `Knn.java`) ao placar adaptativo.

| Experimento (100 rounds, 800×600) | SurfGF | Sniper | legiao_invicta | GFBot |
|---|---|---|---|---|
| v1.1 (base) | 66,5% | 63,6% | ~64% | 90,2% |
| + mira KNN (sem desvio KNN) | 64,9% | 65,6% | 66,1% | — |
| + achatamento (`SURF_VISIT_WEIGHT` 7 / 16) | 63 / 58% | 61 / 53% | 60 / 67% | — (pior ou ruído) |
| + desvio KNN (peso 6) | **70,0%** | **69,2%** | **73,3%** | 87,5% |
| desvio KNN, peso 12 / K=12 / K=48 | 67–74% | 69–74% | 74–76% | 88% |
| tiros do `legiao_invicta` que nos acertam | 14,5% → 10,7% | | | |

Descartados por ficarem dentro do ruído (±5%): distância de órbita 340/520, `SURF_HIT_WEIGHT` 3/14, potência menor. O risco aprendido no melee foi medido três vezes com o mesmo valor e deu 49/41/45, 35/32/40 e 39/37/41 vitórias de 60 — ou seja, **sem melhora comprovada** (mantido por ser inofensivo; o melee é muito ruidoso).

**Torneio aleatório (mesma semente 7, 36 duelos + 8 melees, 20 rounds):** rounds ganhos / pontos do Cleitin-O-BomDeGuerra — legiao_invicta 67%→**89%** / 64%→**81%**; Sniper 71%→**85%** / 64%→**73%**; SurfGF 65%→**72%** / 62%→**69%**; Charger 99%→100% / 85%→87%; GravBot 100% / 98%→96%; DropDodger 100%→92% / 94%→84% (apenas 2 partidas). Regressão de 60 rounds (duelos): 60/60 em 16 dos 19 adversários, GFBot 58/60, SurfGF 47/60, Sniper 47/60; parcela de pontos levemente menor contra adversários fracos (ex.: DropDodger 90%, Strafer 95%).

**Limites:** o resultado vale para estes adversários; robôs de ranking não foram testados. Em partidas curtas o aprendizado tem pouco tempo (o ganho cresce com os rounds).

## 10 rodadas independentes da v1.2 (sementes 101–110; 18 duelos + 4 melees de 20 rounds cada)
Dados: `docs/resultados-anteriores/run10_seed101..110.txt`; análise: `analyze10.ps1`. (A rodada 109 ficou incompleta numa execução anterior e foi refeita junto com a 110.)

| Rodada | Duelos: rounds ganhos | Pontos | Melees: 1º lugar |
|---|---|---|---|
| 101 | 333/360 (92%) | 83% | 65/80 (81%) |
| 102 | 341/360 (95%) | 86% | 43/80 (54%) |
| 103 | 332/360 (92%) | 84% | 66/80 (82%) |
| 104 | 321/360 (89%) | 79% | 67/80 (84%) |
| 105 | 312/360 (87%) | 79% | 59/80 (74%) |
| 106 | 315/360 (88%) | 80% | 47/80 (59%) |
| 107 | 316/360 (88%) | 79% | 58/80 (72%) |
| 108 | 337/360 (94%) | 84% | 68/80 (85%) |
| 109 | 330/360 (92%) | 83% | 49/80 (61%) |
| 110 | 317/360 (88%) | 80% | 50/80 (62%) |
| **Total** | **3254/3600 (90,4%)** | ~82% | **572/800 (72%)**, parcela média 42% |

Por adversário (duelos): GravBot 100% (96% dos pontos) · Charger 99% (84%) · DropDodger 98% (89%) · legiao_invicta 88% (79%) · Sniper 82% (72%) · SurfGF 78% (72%). Por arena: 1200×900 93% · 600×600 91% · 1000×800 90% · 800×600 90% · 800×800 87%.
Piores duelos: SurfGF 12/20 (800×800 e 600×600), Sniper 13/20 (600×600). Piores melees: 6–8 de 20 em cenários com 4–5 fortes juntos (incl. legiao_invicta, Charger, GravBot).

## Versão 1.3 (final) — pontos de melhoria investigados
| Ponto | O que foi feito | Resultado |
|---|---|---|
| Melee | radar em arco (`RADAR_ARC`); varridos `MELEE_PREF_DIST` 260/500, `MELEE_POWER_CAP` 1,7/3,0, `MELEE_MIN_SAFE_DIST` 260 | arco: 71/66/64 vitórias em 100 rounds contra 70/56/61 (leve); demais no ruído → valores mantidos |
| Mira vs surfistas | varridos `AIM_TOL_PX` 5/14, `GUN_DECAY` 0,94/0,985, `KNN_K` 12/60, recência | precisão 13–17% em todas; vitórias 66–88/100 sem padrão → padrões mantidos |
| Aprendizado em partidas curtas | `SURF_KNN_CONF` 10, `SURF_KNN_MIN` 6, `KNN_MIN` 5, recência 0,003 (partidas de 10 rounds, 20 por adversário) | 82–85% dos rounds contra 84% da base → sem ganho; em 10 rounds o Cleitin-O-BomDeGuerra já ganha ~88% |
| Arena 800×800 | 300 rounds em 800×800 vs 800×600 | 226 vs 236 vitórias → era ruído |
Regressão final (60 rounds): SurfGF 68,4% · Sniper 72,0% · legiao_invicta 81,6% · Charger 85,8% · DropDodger 87,4% · GravBot 96,6% · Walls 99,5% · SittingDuck 100% (pontos do Cleitin-O-BomDeGuerra).

## Versão 1.4 — referências reais (Diamond, Reimu, DefMode)
Todos os números abaixo foram medidos em 100 rounds, arena 800×600, `quick.ps1`/`meleebench.ps1`. Detalhes e justificativa: `docs/ANALISE_COMPARATIVA.md`.

| Adversário | v1.3 (base13) | v1.4 |
|---|---|---|
| **Diamond (real, RoboRumble)** | 15/100 (28,7% dos pontos) | 12/100 (28,7%) |
| Reimu (MIT) | 100/100 (90,4%) | 100/100 (92,5%) |
| DefMode (gist) | 98/100 (82,5%) | 97/100 (84,3%) |
| SurfGF / Sniper / legiao_invicta / Charger | — | 82 / 77 / 82 / 98 de 100 |
| v1.4 contra v1.3 (duelo direto) | | 52/100 (50,9% dos pontos) |

Experimentos contra o Diamond (100 rounds; vitórias · acertos do Diamond em nós): linha de base 15 · 12,0%; mira KDE ligada/desligada 11 · 11,8% / 12 · 12,5%; distância 520/620: 16 · 11,5% / 6 · 10,8%; surf com parar+recursão 13 · 12,4%, só recursão 14 · 12,4%, nenhuma 17 · 12,2%; pesos do desvio (visitas 0,5/8/20; KNN 0/25; peso do acerto 1/8): 6–17 vitórias, 11,5–12,5%; desvio multi-janela ligado/desligado 18 · 11,5% / 13 · 12,3%; K do desvio 1/6: 11/14 vitórias.
Melee (4 cenários × 60 rounds, soma de 1os lugares; base 120 e 120): amortecimento 121, razão de energia 113, manter destino 127, amortecimento+destino 116 e 109, as três 106 e 117 → **sem ganho; chaves desligadas**. O cenário D5 (com Diamond) fica em 4–12 de 60.
CPU: 47–89 µs/tick em média (pico 1,9 ms no primeiro tick), `skipped=0`.
**Conclusão honesta:** a v1.4 não supera a v1.3 de forma mensurável; as novas técnicas ficam disponíveis (com chave) mas o robô continua perdendo para o Diamond.
