package cleitin;

/**
 * Parametros ajustaveis do robo (ver secao "Configuracao competitiva" do relatorio).
 * Todos os valores sao constantes de compilacao; mude e recompile.
 */
final class Config {
    private Config() {}

    // ---- Geral ----
    static final boolean LOG = true;                 // imprime 1 linha de estatistica por round
    static final double WALL_MARGIN = 18;            // metade do corpo do robo

    // ---- Movimento 1v1 (wave surfing) ----
    static final boolean SURF = true;                // false = orbita simples sem desvio de ondas (para ablation)
    static final double DESIRED_DISTANCE = 430;      // distancia preferida de orbita em 1v1
    static final double DIST_ARENA_FRAC = 0.72;      // distancia maxima como fracao da menor dimensao da arena
    static final double PASSIVE_DISTANCE = 260;      // distancia quando o inimigo nunca atira
    static final double WALL_STICK = 130;            // comprimento do "stick" do wall smoothing
    static final double SURF_HIT_WEIGHT = 7.0;       // peso dos acertos reais no perigo
    static final double SURF_VISIT_WEIGHT = 2.2;     // peso das "visitas" (onde estivemos quando a onda passou)
    static final double SURF_PRIOR_WEIGHT = 1.6;     // peso do prior (tiro direto / linear)
    static final double SURF_KNN_WEIGHT = 10.0;       // peso do perigo previsto por uma mira KNN inimiga (0 = desligado)
    static final double SURF_KNN_HIT_W = 3.0;        // peso de um acerto real frente a uma visita no historico KNN
    static final double SURF_KNN_CONF = 25;          // amostras para metade da confianca no KNN do desvio
    static final int    SURF_KNN_MIN = 12;           // minimo de amostras para usar o KNN do desvio
    static final double SURF_KNN_RECENCY = 0.0;      // peso por idade das amostras do desvio (0 = igual)
    static final int    SURF_KNN_K = 24;             // vizinhos do desvio KNN
    static final boolean SURF_STOP = false;           // inclui a opcao "parar" no desvio
    static final double SURF_STOP_BIAS = 1.0;        // <1 favorece parar
    static final boolean SURF_RECURSE = false;        // 2a onda: melhor das 3 opcoes (em vez de repetir a mesma)
    static final boolean SURF_MULTI = true;          // desvio imita mira KNN multi-janela
    static final int    SURF_MULTI_K = 3;            // vizinhos por janela no desvio
    static final double SURF_SECOND_WAVE = 0.45;     // peso da 2a onda na decisao
    static final double SURF_HYSTERESIS = 0.04;      // diferenca relativa minima para trocar de sentido

    // ---- Movimento melee (risco minimo) ----
    static final double MELEE_MIN_SAFE_DIST = 160;
    static final double MELEE_PREF_DIST = 360;
    static final double MELEE_ATTRACT = 1.2e-8;      // atracao para a distancia preferida do alvo
    static final boolean MELEE_CROWD = false;         // amortece inimigos protegidos por outros mais proximos
    static final boolean MELEE_ENERGY_RATIO = false;  // peso do inimigo = energia/nossa energia (0,25..4)
    static final boolean MELEE_KEEP_DEST = false;     // viés (x0,8) para manter o destino anterior
    static final double MELEE_WAVE_K = 0.004;        // peso do perigo aprendido das ondas inimigas no melee
    static final double MELEE_WALL_RISK = 0.0006;

    // ---- Radar / melee ----
    static final boolean RADAR_ARC = true;           // melee: varre so o arco dos inimigos
    static final double RADAR_ARC_PAD = 0.4;         // folga (rad) alem dos inimigos
    static final double RADAR_ARC_MAX = 4.8;         // acima disso gira tudo
    static final double MELEE_POWER_CAP = 2.2;       // potencia maxima em melee

    // ---- Mira ----
    static final double AIM_TOL_PX = 9;              // tolerancia lateral para disparar (pixels)
    static final int    MAX_PREDICT = 160;           // ticks maximos na previsao de trajetoria
    static final int    MAX_FIRE_AGE = 3;            // idade maxima (ticks) do dado do alvo para atirar
    static final double GUN_DECAY = 0.965;           // memoria do placar das miras virtuais
    static final double GF_DECAY = 0.992;            // memoria dos histogramas GuessFactor
    static final double KNN_RECENCY = 0.0;          // peso por idade das amostras da mira (0 = igual)
    static final int    KNN_MIN = 10;                // minimo de amostras para a mira KNN
    static final boolean KDE_GUN = true;             // mira de densidade (KDE) multi-janela
    static final int[]  KDE_VIEWS = {125, 400, 1500, 4000};
    static final int    KDE_K = 3;                   // vizinhos por janela
    static final int    KNN_K = 28;                  // vizinhos consultados pela mira KNN
    static final int    VIRTUAL_WAVE_EVERY = 2;      // gera onda de aprendizado a cada N ticks

    // ---- Adaptacao a adversarios dificeis ----
    static final double HARD_Q = 0.14;               // taxa de acerto abaixo da qual o alvo e "dificil" (surfista)
    static final double HARD_POWER_CAP = 3.0;        // teto de potencia contra alvos dificeis (3.0 = desligado)
    static final double CLOSE_THRESH = 0.12;         // fracao de scans a <200 px a partir da qual o inimigo e "agressor"
    static final double CLOSE_SCALE = 1.6;           // fator da distancia de orbita contra agressores (1.0 = desligado)

    // ---- Energia ----
    static final double POWER_RANGE_REF = 900;       // referencia para potencia por distancia
    static final double POWER_RANGE_DIV = 270;
}
