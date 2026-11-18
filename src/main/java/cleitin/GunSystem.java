package cleitin;

import java.util.ArrayList;

import robocode.Bullet;

/**
 * GunSystem: mira, previsao e disparo.
 *
 * Miras virtuais: HEAD (tiro direto), LIN (alvo linear), CIRC (alvo circular), GFA (GuessFactor segmentado),
 * GFB (GuessFactor grosso). Cada onda registra o angulo de TODAS as miras; quando a onda alcanca o alvo,
 * cada mira e pontuada (acertou ou nao) e os histogramas GF sao atualizados. A mira com melhor taxa de acerto
 * (com decaimento) contra aquele alvo e a usada.
 *
 * Ordem do Robocode: o tiro sai com a direcao do canhao NO INICIO do tick (antes do giro do tick). Por isso a mira
 * e calculada no tick anterior para o instante de disparo (agora+1) e o canhao so dispara se ja estiver alinhado.
 */
final class GunSystem {
    static final int HEAD = 0, LIN = 1, CIRC = 2, GFA = 3, GFB = 4, GFC = 5, KNN = 6, KDE = 7, N = 8;
    static final String[] NAMES = {"head", "linear", "circular", "gfSeg", "gfCoarse", "gfTime", "knn", "kde"};

    private final Cleitin_O_BomDeGuerra r;
    private final ArrayList<MyWave> waves = new ArrayList<MyWave>();
    private final double[] tmp = new double[4];

    // mira pendente (calculada no tick anterior para o tick atual)
    private Enemy pTarget;
    private long pTime = -1;
    private double pPower, pAim, pEx, pEy, pMea, pDist;
    private int pDir, pSegA, pSegB, pSegC, pGun;
    private double[] pFeat;
    private final double[] pAngles = new double[N];

    /** Contador de uso de cada mira (diagnostico). */
    final int[] gunUse = new int[N];

    GunSystem(Cleitin_O_BomDeGuerra r) {
        this.r = r;
    }

    void newRound() {
        waves.clear();
        pTarget = null;
        pTime = -1;
    }

    void onEnemyDeath(Enemy e) {
        for (int i = waves.size() - 1; i >= 0; i--) if (waves.get(i).target == e) waves.remove(i);
        if (pTarget == e) { pTarget = null; pTime = -1; }
    }

    // ---------------------------------------------------------------------------------------------

    void tick(Enemy target, double power, long now) {
        // 1) disparo com a mira calculada no tick anterior
        boolean fired = false;
        boolean pendingOk = target != null && pTarget == target && pTime == now && pPower > 0 && target.alive
                && now - target.lastSeen <= Config.MAX_FIRE_AGE;
        if (pendingOk) {
            double diff = Util.normRel(pAim - r.getGunHeadingRadians());
            double tol = Math.atan(Config.AIM_TOL_PX / Math.max(pDist, 40));
            if (r.getGunHeat() == 0 && Math.abs(diff) <= tol && r.getEnergy() > pPower + 0.05) {
                Bullet b = r.setFireBullet(pPower);
                if (b != null) {
                    fired = true;
                    target.shotsAtIt++;
                    Cleitin_O_BomDeGuerra.STATS.onFire();
                    gunUse[pGun]++;
                    makeWave(now, true);
                }
            }
        }
        if (!fired && pendingOk && (now % Config.VIRTUAL_WAVE_EVERY) == 0) makeWave(now, false);

        // 2) resolver ondas que alcancaram o alvo
        resolve(now);

        // 3) mirar para o proximo tick
        computeAim(target, power, now);
    }

    // ---------------------------------------------------------------------------------------------

    private void makeWave(long now, boolean real) {
        MyWave w = new MyWave();
        w.ox = r.getX();
        w.oy = r.getY();
        w.fireTime = now;
        w.power = pPower;
        w.speed = Util.bulletSpeed(pPower);
        w.mea = pMea;
        w.dir = pDir;
        w.refAngle = Util.angle(w.ox, w.oy, pEx, pEy);
        System.arraycopy(pAngles, 0, w.angles, 0, N);
        w.segA = pSegA;
        w.segB = pSegB;
        w.segC = pSegC;
        w.feat = pFeat;
        w.target = pTarget;
        w.real = real;
        waves.add(w);
    }

    private void resolve(long now) {
        double fw = r.getBattleFieldWidth(), fh = r.getBattleFieldHeight();
        for (int i = waves.size() - 1; i >= 0; i--) {
            MyWave w = waves.get(i);
            Enemy e = w.target;
            if (!e.alive) { waves.remove(i); continue; }
            double traveled = (now - w.fireTime) * w.speed;
            double ex, ey;
            int age = (int) (now - e.lastSeen);
            if (age <= 0) { ex = e.x; ey = e.y; }
            else { e.project(age, fw, fh, tmp); ex = tmp[0]; ey = tmp[1]; }
            double d = Util.dist(w.ox, w.oy, ex, ey);
            if (traveled >= d) {
                if (age <= 2) score(w, e, Util.angle(w.ox, w.oy, ex, ey), d);
                waves.remove(i);
            } else if (traveled > 1300) {
                waves.remove(i);
            }
        }
    }

    private void score(MyWave w, Enemy e, double ang, double d) {
        double gf = Util.lim(-1, Util.normRel(ang - w.refAngle) * w.dir / w.mea, 1);
        double halfW = Math.atan(20.0 / Math.max(d, 20));
        for (int g = 0; g < N; g++) {
            boolean hit = Math.abs(Util.normRel(w.angles[g] - ang)) <= halfW;
            e.gunHit[g] = e.gunHit[g] * Config.GUN_DECAY + (hit ? 1 : 0);
            e.gunTrials[g] = e.gunTrials[g] * Config.GUN_DECAY + 1;
        }
        int c = Gf.bin(gf);
        if (e.gfA == null) {
            e.gfA = new double[180][Gf.BINS];
            e.gfB = new double[9][Gf.BINS];
            e.gfC = new double[48][Gf.BINS];
        }
        Gf.add(e.gfA[w.segA], c, 1, Config.GF_DECAY);
        Gf.add(e.gfB[w.segB], c, 1, Config.GF_DECAY);
        Gf.add(e.gfC[w.segC], c, 1, Config.GF_DECAY);
        if (w.feat != null) { e.knn.add(w.feat, gf); e.knnAS.add(w.feat, gf); }
    }

    // ---------------------------------------------------------------------------------------------

    private void computeAim(Enemy t, double power, long now) {
        if (t == null || !t.alive || power <= 0) { pTarget = null; pTime = -1; return; }
        double fw = r.getBattleFieldWidth(), fh = r.getBattleFieldHeight();
        int steps = (int) (now - t.lastSeen) + 1;
        t.project(steps, fw, fh, tmp);
        double ex = tmp[0], ey = tmp[1], eh = tmp[2], ev = tmp[3];

        double m = Config.WALL_MARGIN;
        double mx = Util.lim(m, r.getX() + Math.sin(r.getHeadingRadians()) * r.getVelocity(), fw - m);
        double my = Util.lim(m, r.getY() + Math.cos(r.getHeadingRadians()) * r.getVelocity(), fh - m);

        double bs = Util.bulletSpeed(power);
        double dist = Util.dist(mx, my, ex, ey);
        double absB = Util.angle(mx, my, ex, ey);
        double mea = Util.mea(bs);

        double lat = ev * Math.sin(eh - absB);
        int dir = Math.abs(lat) > 0.1 ? (lat > 0 ? 1 : -1) : t.latDir;

        pAngles[HEAD] = absB;
        pAngles[LIN] = predict(mx, my, ex, ey, eh, ev, 0, bs, fw, fh);
        pAngles[CIRC] = predict(mx, my, ex, ey, eh, ev, t.turnRate, bs, fw, fh);

        // segmentacao
        double accel = Math.abs(ev) - Math.abs(t.prevVelocity);
        int accB = accel < -0.5 ? 0 : (accel > 0.5 ? 2 : 1);
        double ray = Util.rayToWall(ex, ey, ev >= 0 ? eh : eh + Math.PI, m, fw - m, m, fh - m);
        double tw = ray / Math.max(Math.abs(ev), 0.5);
        double ratio = tw / Math.max(dist / bs, 1);
        int wallB = ratio < 0.7 ? 0 : (ratio < 1.3 ? 1 : 2);
        int dB5 = dist < 200 ? 0 : dist < 350 ? 1 : dist < 500 ? 2 : dist < 650 ? 3 : 4;
        double al = Math.abs(lat);
        int lB4 = al < 1 ? 0 : al < 3.5 ? 1 : al < 6.5 ? 2 : 3;
        int dB3 = dist < 300 ? 0 : dist < 500 ? 1 : 2;
        int lB3 = al < 1 ? 0 : al < 5 ? 1 : 2;
        pSegA = ((dB5 * 4 + lB4) * 3 + accB) * 3 + wallB;
        pSegB = dB3 * 3 + lB3;
        long since = now + 1 - t.dirChangeTime;
        int tB = since < 10 ? 0 : since < 25 ? 1 : since < 50 ? 2 : 3;
        int vB = Math.abs(ev) < 2 ? 0 : Math.abs(ev) < 6 ? 1 : 2;
        pSegC = ((dist < 250 ? 0 : dist < 450 ? 1 : dist < 650 ? 2 : 3) * 4 + tB) * 3 + vB;

        // GuessFactor
        if (t.gfA == null) {
            t.gfA = new double[180][Gf.BINS];
            t.gfB = new double[9][Gf.BINS];
            t.gfC = new double[48][Gf.BINS];
        }
        double[] a = t.gfA[pSegA], b = t.gfB[pSegB];
        double sa = 0, sb = 0;
        for (int i = 0; i < Gf.BINS; i++) { sa += a[i]; sb += b[i]; }
        double gfB = binToGf(b, sb);
        double gfA;
        if (sa < 1e-6) {
            gfA = gfB;
        } else {
            double wa = sa / (sa + 6 * Gf.KMASS);   // confianca cresce com o numero de amostras do segmento
            double[] mix = new double[Gf.BINS];
            double ia = 1.0 / sa, ib = sb > 1e-6 ? 1.0 / sb : 0;
            for (int i = 0; i < Gf.BINS; i++) mix[i] = wa * a[i] * ia + (1 - wa) * b[i] * ib;
            gfA = Gf.gfOfBin(Gf.argmax(mix));
        }
        pAngles[GFA] = absB + dir * gfA * mea;
        pAngles[GFB] = absB + dir * gfB * mea;
        double[] c3 = t.gfC[pSegC];
        double sc = 0;
        for (int i = 0; i < Gf.BINS; i++) sc += c3[i];
        pAngles[GFC] = absB + dir * (sc < 1e-6 ? gfB : Gf.gfOfBin(Gf.argmax(c3))) * mea;
        double[] kf = new double[Knn.F];
        kf[0] = Math.min(1, Math.abs(lat) / 8);
        kf[1] = (Util.lim(-8, -ev * Math.cos(eh - absB), 8) + 8) / 16;
        kf[2] = Util.lim(-2, accel, 2) / 4 + 0.5;
        kf[3] = Math.min(1, dist / 900);
        kf[4] = Math.min(1, ratio / 2.0);
        kf[5] = Math.min(60, since) / 60.0;
        kf[6] = Util.lim(0, Math.min(Math.min(ex - m, fw - m - ex), Math.min(ey - m, fh - m - ey)) / 300, 1);
        kf[7] = (bs - 11) / 9;
        pFeat = kf;
        double kg = t.knn.query(kf, Config.KNN_K);
        pAngles[KNN] = absB + dir * (Double.isNaN(kg) ? gfB : kg) * mea;
        double kd = Config.KDE_GUN ? kde(t, kf, dist, mea) : Double.NaN;
        pAngles[KDE] = absB + dir * (Double.isNaN(kd) ? (Double.isNaN(kg) ? gfB : kg) : kd) * mea;

        pGun = bestGun(t);
        pAim = pAngles[pGun];
        pTarget = t;
        pTime = now + 1;
        pPower = power;
        pEx = ex; pEy = ey; pMea = mea; pDir = dir; pDist = dist;

        r.setTurnGunRightRadians(Util.normRel(pAim - r.getGunHeadingRadians()));
    }

    private final double[] kGf = new double[64], kW = new double[64];

    /** Estimativa de densidade (kernel gaussiano, largura de banda = 2x a largura angular do alvo) sobre 59 GFs. */
    private double kde(Enemy t, double[] kf, double dist, double mea) {
        if (t.knnAS.size() < 10) return Double.NaN;
        int n = t.knnAS.multiView(kf, Config.KDE_VIEWS, Config.KDE_K, kGf, kW);
        if (n == 0) return Double.NaN;
        double bw = 2 * Math.atan(18.0 / Math.max(dist, 40)) / mea;
        double best = -1, bestGf = 0;
        for (int c = 0; c < 59; c++) {
            double g = -1 + c * (2.0 / 58);
            double dens = 0;
            for (int i = 0; i < n; i++) {
                double u = (g - kGf[i]) / bw;
                dens += kW[i] * Math.exp(-0.5 * u * u);
            }
            if (dens > best) { best = dens; bestGf = g; }
        }
        return bestGf;
    }

    private static double binToGf(double[] h, double sum) {
        if (sum < 1e-6) return 0;
        return Gf.gfOfBin(Gf.argmax(h));
    }

    private int bestGun(Enemy t) {
        int best = GFA;
        double bv = -1;
        for (int g = 0; g < N; g++) {
            double v = t.gunHit[g] / t.gunTrials[g];
            if (g >= GFA) v += 0.004;      // desempate a favor das miras aprendidas
            if (v > bv) { bv = v; best = g; }
        }
        return best;
    }

    /** Previsao iterativa: angulo para interceptar o alvo com velocidade/rumo (e giro) constantes. */
    private double predict(double mx, double my, double ex, double ey, double eh, double ev, double turn,
                           double bs, double fw, double fh) {
        double px = ex, py = ey, h = eh;
        double m = Config.WALL_MARGIN;
        double cap = Util.maxTurn(ev) + 0.001;
        double tr = Util.lim(-cap, turn, cap);
        double v = ev;
        for (int k = 1; k <= Config.MAX_PREDICT; k++) {
            h += tr;
            px += Math.sin(h) * v;
            py += Math.cos(h) * v;
            if (px < m || px > fw - m || py < m || py > fh - m) {
                px = Util.lim(m, px, fw - m);
                py = Util.lim(m, py, fh - m);
                v = 0; tr = 0;
            }
            if (k * bs >= Util.dist(mx, my, px, py)) break;
        }
        return Util.angle(mx, my, px, py);
    }

    String describeUse() {
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < N; g++) sb.append(NAMES[g]).append('=').append(gunUse[g]).append(' ');
        return sb.toString();
    }
}
