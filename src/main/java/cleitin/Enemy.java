package cleitin;

/**
 * EnemyModel: estado observado + estatisticas aprendidas de UM adversario.
 * Os campos "aprendidos" (histogramas, placares) persistem entre rounds (a instancia vive em Cleitin_O_BomDeGuerra.DB).
 */
final class Enemy {
    static final int SURF_CTX = 9;
    static final double SURF_DECAY_HIT = 0.985;
    static final double SURF_DECAY_VIS = 0.992;

    final String name;

    // ---- estado observado (confirmado pelo radar) ----
    boolean alive;
    long lastSeen = -1, prevSeen = -1;
    double x, y, heading, velocity, energy, distance, absBearing;
    double prevX, prevY, prevHeading, prevVelocity, prevEnergy;
    double lat, adv;            // velocidade lateral / de aproximacao em relacao a nos
    int latDir = 1;             // +1 = horario em torno de nos
    double turnRate;            // rad/tick (suavizado)
    double speedEma;            // |v| medio observado
    double closeEma;            // fracao de scans com o inimigo a <200 px (agressor de curto alcance)
    double dirChangeEma;        // frequencia de inversao de sentido
    double expectedDrop;        // energia que o inimigo perdeu por nossos tiros/colisoes desde o ultimo scan
    double expectedGain;        // energia que ele ganhou acertando-nos desde o ultimo scan

    // ---- estimativas / estatisticas (persistentes) ----
    int shotsDetected;          // tiros inferidos por queda de energia (ESTIMATIVA)
    double avgShotPower;
    long lastShotTime = -1000;
    int hitsOnUs;               // confirmado: onHitByBullet
    int shotsAtIt, hitsOnIt;    // confirmado: nossos tiros reais / acertos
    double dmgDealt, dmgTaken;  // confirmado

    // ---- placar das miras virtuais (decaimento exponencial) ----
    final double[] gunHit = new double[GunSystem.N];
    final double[] gunTrials = new double[GunSystem.N];

    // ---- histogramas GF de mira (alocados sob demanda) ----
    double[][] gfA, gfB, gfC;
    final Knn knn = new Knn(1800, Config.KNN_RECENCY);
    final Knn knnAS = new Knn(4000, 0);   // memoria longa para a mira KDE multi-janela
    final Knn surfKnn = new Knn(4000, Config.SURF_KNN_RECENCY);   // onde uma mira KNN inimiga nos acertaria (acertos + visitas)
    long dirChangeTime;

    // ---- estatisticas de surf (onde NOS fomos atingidos / onde estivemos) ----
    final double[][] surfHit = new double[SURF_CTX][Gf.BINS];
    final double[][] surfVis = new double[SURF_CTX][Gf.BINS];
    final double[] surfHitTot = new double[SURF_CTX];
    final double[] surfVisTot = new double[SURF_CTX];
    final double[] surfHitG = new double[Gf.BINS];
    final double[] surfVisG = new double[Gf.BINS];
    double surfHitTotG, surfVisTotG;
    int hitsLearned;

    Enemy(String name) {
        this.name = name;
        for (int g = 0; g < GunSystem.N; g++) {
            gunHit[g] = 0.6;     // prior fraco: 10% de acerto com peso de 6 amostras
            gunTrials[g] = 6;
        }
    }

    void newRound() {
        alive = false;
        lastSeen = -1;
        prevSeen = -1;
        expectedDrop = 0;
        expectedGain = 0;
        turnRate = 0;
        lastShotTime = -1000;
    }

    /** Atualiza com um scan (posicao do radar em relacao a nos). */
    void update(double myX, double myY, double myHeading, double bearing, double dist,
                double hd, double v, double en, long time) {
        boolean first = lastSeen < 0;
        prevX = x; prevY = y; prevHeading = heading; prevVelocity = velocity;
        prevEnergy = energy; prevSeen = lastSeen;

        distance = dist;
        absBearing = myHeading + bearing;
        x = myX + Math.sin(absBearing) * dist;
        y = myY + Math.cos(absBearing) * dist;
        heading = hd;
        velocity = v;
        energy = en;
        alive = true;

        if (first) {
            prevX = x; prevY = y; prevHeading = hd; prevVelocity = v; prevEnergy = en;
            turnRate = 0;
        } else {
            long dt = Math.max(1, time - prevSeen);
            double tr = Util.normRel(hd - prevHeading) / dt;
            double cap = Util.maxTurn(v) * 1.2 + 0.01;
            tr = Util.lim(-cap, tr, cap);
            turnRate = 0.4 * turnRate + 0.6 * tr;
            speedEma = 0.97 * speedEma + 0.03 * Math.abs(v);
            closeEma = 0.995 * closeEma + 0.005 * (dist < 200 ? 1 : 0);
            boolean flip = v * prevVelocity < -0.5;
            if (flip || Math.abs(v) < 0.3) dirChangeTime = time;
            dirChangeEma = 0.98 * dirChangeEma + 0.02 * (flip ? 1 : 0);
        }
        double rel = hd - absBearing;
        lat = v * Math.sin(rel);
        adv = -v * Math.cos(rel);
        if (Math.abs(lat) > 0.1) latDir = lat > 0 ? 1 : -1;
        lastSeen = time;
    }

    /** Posicao linear extrapolada 'ticks' ticks apos o ultimo scan (limitada ao campo). out = {x, y, heading, velocity}. */
    void project(int ticks, double fieldW, double fieldH, double[] out) {
        double px = x, py = y, v = velocity;
        double vx = Math.sin(heading) * v, vy = Math.cos(heading) * v;
        double m = Config.WALL_MARGIN;
        for (int k = 0; k < ticks; k++) {
            px += vx; py += vy;
            if (px < m || px > fieldW - m || py < m || py > fieldH - m) {
                px = Util.lim(m, px, fieldW - m);
                py = Util.lim(m, py, fieldH - m);
                vx = 0; vy = 0; v = 0;
                break;
            }
        }
        out[0] = px; out[1] = py; out[2] = heading; out[3] = v;
    }

    // ---------------- Aprendizado de surf ----------------

    void learnHit(int ctx, double gf) {
        int c = Gf.bin(gf);
        Gf.add(surfHit[ctx], c, 1, SURF_DECAY_HIT);
        surfHitTot[ctx] = surfHitTot[ctx] * SURF_DECAY_HIT + 1;
        Gf.add(surfHitG, c, 1, SURF_DECAY_HIT);
        surfHitTotG = surfHitTotG * SURF_DECAY_HIT + 1;
        hitsLearned++;
    }

    void learnVisit(int ctx, double gf) {
        int c = Gf.bin(gf);
        Gf.add(surfVis[ctx], c, 1, SURF_DECAY_VIS);
        surfVisTot[ctx] = surfVisTot[ctx] * SURF_DECAY_VIS + 1;
        Gf.add(surfVisG, c, 1, SURF_DECAY_VIS);
        surfVisTotG = surfVisTotG * SURF_DECAY_VIS + 1;
    }

    private static double share(double[] arr, double tot, double gf, double halfW, double k) {
        if (tot <= 0) return 0;
        double s = Gf.window(arr, gf, halfW) / (Gf.KMASS * tot);
        return s * (tot / (tot + k));
    }

    /** Perigo (>=0) de estar no GuessFactor gf quando uma onda deste inimigo passar. */
    double danger(int ctx, double gf, double halfW, double priorGfLinear, double[] pdf, double pdfConf) {
        double hit = 0.65 * share(surfHit[ctx], surfHitTot[ctx], gf, halfW, 3)
                + 0.35 * share(surfHitG, surfHitTotG, gf, halfW, 3);
        double vis = 0.65 * share(surfVis[ctx], surfVisTot[ctx], gf, halfW, 8)
                + 0.35 * share(surfVisG, surfVisTotG, gf, halfW, 8);
        double pw = Config.SURF_PRIOR_WEIGHT / (1.0 + 0.6 * hitsLearned);
        double d0 = gf / 0.18, d1 = (gf - priorGfLinear) / 0.18;
        double prior = pw * 0.5 * (Math.exp(-0.5 * d0 * d0) + Math.exp(-0.5 * d1 * d1));
        double knn = pdf == null ? 0 : pdfConf * Gf.window(pdf, gf, halfW);
        return Config.SURF_HIT_WEIGHT * hit + Config.SURF_VISIT_WEIGHT * vis + prior + Config.SURF_KNN_WEIGHT * knn;
    }

    /** Taxa de acerto ponderada da melhor mira virtual. */
    double bestGunRate() {
        double b = 0;
        for (int g = 0; g < GunSystem.N; g++) b = Math.max(b, gunHit[g] / gunTrials[g]);
        return b;
    }
}
