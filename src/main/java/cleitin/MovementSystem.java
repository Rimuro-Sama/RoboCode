package cleitin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import robocode.Bullet;

/**
 * MovementSystem.
 *
 * 1v1: Wave Surfing. Cada queda de energia do inimigo (0.1..3.0, descontados danos/ganhos conhecidos) gera uma
 * ESTIMATIVA de onda de tiro. Para as 2 ondas mais proximas simulamos a orbita em cada sentido (mesma fisica do
 * robo real: giro limitado, aceleracao 1, desaceleracao 2, wall smoothing) e escolhemos o sentido cujo ponto de
 * impacto previsto tem menor perigo (histogramas de GuessFactor aprendidos de acertos reais e de "visitas",
 * mais um prior de tiro direto/linear).
 *
 * Melee: movimento de risco minimo: candidatos em volta, risco = inimigos (energia/distancia^2 x perpendicularidade)
 * + paredes/cantos + ondas inimigas estimadas + atracao suave pela distancia preferida do alvo.
 */
final class MovementSystem {
    private final Cleitin_O_BomDeGuerra r;
    private final ArrayList<EnemyWave> waves = new ArrayList<EnemyWave>();
    private final double[] hx = new double[4], hy = new double[4], hh = new double[4], hv = new double[4];
    private final Random rnd = new Random();
    private final double[] tmp = new double[4];
    private final double[] sim = new double[4];
    private final double[] proj = new double[4];

    private int orbitDir = 1;
    private long nextFlip;
    private int lastWaveDir = 1;
    private int stuck;
    private long lastLatFlip;
    private int lastLatSign;
    // melee
    private double goalX, goalY;
    private long goalTime = -100;
    private double fw, fh;
    private Collection<Enemy> allRef;

    MovementSystem(Cleitin_O_BomDeGuerra r) {
        this.r = r;
    }

    void newRound() {
        waves.clear();
        goalTime = -100;
        stuck = 0;
        nextFlip = 0;
        orbitDir = rnd.nextBoolean() ? 1 : -1;
    }

    void recordState(long now) {
        int i = (int) (now & 3);
        hx[i] = r.getX();
        hy[i] = r.getY();
        hh[i] = r.getHeadingRadians();
        hv[i] = r.getVelocity();
    }

    // ---------------------------------------------------------------------------------------------
    // Deteccao de tiros inimigos (estimativa por queda de energia)

    void onEnemyScan(Enemy e, long now) {
        double drop = 0;
        boolean usable = e.prevSeen >= 0;
        if (usable) {
            long dt = now - e.prevSeen;
            double fired = e.prevEnergy - e.energy - e.expectedDrop + e.expectedGain;
            if (dt >= 1 && dt <= 2 && fired > 0.09 && fired < 3.01 && !isWallHit(e, fired)) drop = fired;
            if (drop > 0) {
                e.shotsDetected++;
                e.avgShotPower = e.avgShotPower == 0 ? drop : 0.9 * e.avgShotPower + 0.1 * drop;
                e.lastShotTime = now;
                createWave(e, drop, now, dt);
            }
        }
        e.expectedDrop = 0;
        e.expectedGain = 0;
    }

    private boolean isWallHit(Enemy e, double fired) {
        double m = Config.WALL_MARGIN + 6;
        boolean near = e.x < m || e.x > r.getBattleFieldWidth() - m || e.y < m || e.y > r.getBattleFieldHeight() - m;
        if (!near) return false;
        double wallDmg = Math.max(Math.abs(e.prevVelocity) / 2 - 1, 0);
        return Math.abs(fired - wallDmg) < 0.25;
    }

    private void createWave(Enemy e, double power, long now, long dt) {
        long fireTime = now - 1;
        double ox, oy;
        if (dt == 1) {
            ox = e.prevX; oy = e.prevY;
        } else {
            ox = e.prevX + Math.sin(e.prevHeading) * e.prevVelocity * (dt - 1);
            oy = e.prevY + Math.cos(e.prevHeading) * e.prevVelocity * (dt - 1);
        }
        int i = (int) (fireTime & 3);
        double mx = hx[i], my = hy[i];
        EnemyWave w = new EnemyWave();
        w.src = e;
        w.ox = ox; w.oy = oy;
        w.power = power;
        w.speed = Util.bulletSpeed(power);
        w.mea = Util.mea(w.speed);
        w.fireTime = fireTime;
        w.directAngle = Util.angle(ox, oy, mx, my);
        double lat = hv[i] * Math.sin(hh[i] - w.directAngle);
        if (Math.abs(lat) > 0.1) lastWaveDir = lat > 0 ? 1 : -1;
        w.dir = lastWaveDir;
        double d = Util.dist(ox, oy, mx, my);
        int dB = d < 300 ? 0 : d < 500 ? 1 : 2;
        double al = Math.abs(hv[i]);
        int sB = al < 2 ? 0 : al < 6 ? 1 : 2;
        w.ctx = dB * 3 + sB;
        // prior linear: onde estaremos se mantivermos velocidade e rumo ate o impacto
        double t = d / w.speed;
        double px = Util.lim(Config.WALL_MARGIN, mx + Math.sin(hh[i]) * hv[i] * t, fw() - Config.WALL_MARGIN);
        double py = Util.lim(Config.WALL_MARGIN, my + Math.cos(hh[i]) * hv[i] * t, fh() - Config.WALL_MARGIN);
        w.gfLinear = Util.lim(-1, Util.normRel(Util.angle(ox, oy, px, py) - w.directAngle) * w.dir / w.mea, 1);
        buildSurfFeatures(w, i, d, now);
        if (allRef != null) {
            int closer = 0;
            for (Enemy o : allRef) if (o != e && o.alive && Util.dist(ox, oy, o.x, o.y) < d) closer++;
            w.weight = 1.0 / (1 + closer);
        }
        waves.add(w);
        Cleitin_O_BomDeGuerra.STATS.enemyWaves++;
    }

    /** Caracteristicas da NOSSA situacao no momento do tiro inimigo e densidade de GF prevista por uma mira KNN inimiga. */
    private void buildSurfFeatures(EnemyWave w, int i, double d, long now) {
        double mx = hx[i], my = hy[i], mh = hh[i], mv = hv[i];
        double th = w.directAngle;
        double lat = mv * Math.sin(mh - th);
        double adv = -mv * Math.cos(mh - th);
        double accel = Math.abs(mv) - Math.abs(hv[(i + 3) & 3]);
        double m = Config.WALL_MARGIN;
        double ray = Util.rayToWall(mx, my, mv >= 0 ? mh : mh + Math.PI, m, fw() - m, m, fh() - m);
        double ratio = (ray / Math.max(Math.abs(mv), 0.5)) / Math.max(d / w.speed, 1);
        long since = Math.max(0, w.fireTime - lastLatFlip);
        double[] f = new double[Knn.F];
        f[0] = Math.min(1, Math.abs(lat) / 8);
        f[1] = (Util.lim(-8, adv, 8) + 8) / 16;
        f[2] = Util.lim(-2, accel, 2) / 4 + 0.5;
        f[3] = Math.min(1, d / 900);
        f[4] = Math.min(1, ratio / 2.0);
        f[5] = Math.min(60, since) / 60.0;
        f[6] = Util.lim(0, Math.min(Math.min(mx - m, fw() - m - mx), Math.min(my - m, fh() - m - my)) / 300, 1);
        f[7] = (w.speed - 11) / 9;
        w.feat = f;
        if (Config.SURF_MULTI && Config.SURF_KNN_WEIGHT > 0) {
            // imita uma mira KNN adversaria de varias janelas: 4 memorias (recente..longa), k vizinhos cada, banda ~ largura do robo
            Knn kn = w.src.surfKnn;
            if (kn.size() >= Config.SURF_KNN_MIN) {
                int n = kn.multiView(f, Config.KDE_VIEWS, Config.SURF_MULTI_K, mvGf, mvW);
                if (n > 0) {
                    double[] buf = new double[Gf.BINS];
                    double bw = 2 * Math.atan(18.0 / Math.max(d, 40)) / w.mea;
                    double sum = 0;
                    for (int b = 0; b < Gf.BINS; b++) {
                        double g = Gf.gfOfBin(b), dens = 0;
                        for (int q = 0; q < n; q++) {
                            double u = (g - mvGf[q]) / bw;
                            dens += mvW[q] * Math.exp(-0.5 * u * u);
                        }
                        buf[b] = dens;
                        sum += dens;
                    }
                    if (sum > 1e-9) {
                        for (int b = 0; b < Gf.BINS; b++) buf[b] /= sum;
                        w.pdf = buf;
                        w.pdfConf = kn.size() / (kn.size() + Config.SURF_KNN_CONF);
                        return;
                    }
                }
            }
        }
        if (Config.SURF_KNN_WEIGHT > 0) {
            double[] buf = new double[Gf.BINS];
            if (w.src.surfKnn.density(f, Config.SURF_KNN_K, buf)) {
                w.pdf = buf;
                int n = w.src.surfKnn.size();
                w.pdfConf = n / (n + Config.SURF_KNN_CONF);
            }
        }
    }

    private final double[] mvGf = new double[64], mvW = new double[64];

    private double fw() { return r.getBattleFieldWidth(); }
    private double fh() { return r.getBattleFieldHeight(); }

    // ---------------------------------------------------------------------------------------------
    // Eventos

    void onHitByBullet(Enemy src, Bullet b, long now) {
        if (src == null) return;
        double mx = r.getX(), my = r.getY();
        EnemyWave best = null;
        double bd = 70;
        for (EnemyWave w : waves) {
            if (w.src != src) continue;
            if (Math.abs(w.speed - b.getVelocity()) > 0.15) continue;
            double diff = Math.abs(w.traveled(now) - Util.dist(w.ox, w.oy, mx, my));
            if (diff < bd) { bd = diff; best = w; }
        }
        if (best != null) {
            Cleitin_O_BomDeGuerra.STATS.hitsMatched++;
            src.learnHit(best.ctx, gfOf(best, mx, my));
            if (best.feat != null) src.surfKnn.add(best.feat, gfOf(best, mx, my), Config.SURF_KNN_HIT_W);
            waves.remove(best);
        }
    }

    void onWallOrRobot() {
        orbitDir = -orbitDir;
        goalTime = -100;
    }

    void onEnemyDeath(Enemy e) {
        for (int i = waves.size() - 1; i >= 0; i--) if (waves.get(i).src == e) waves.remove(i);
    }

    // ---------------------------------------------------------------------------------------------

    void tick(Enemy target, Collection<Enemy> all, int others, long now) {
        fw = fw();
        if (target != null && target.lastSeen >= 0) {
            double th = Util.angle(target.x, target.y, r.getX(), r.getY());
            double lat = r.getVelocity() * Math.sin(r.getHeadingRadians() - th);
            if (Math.abs(lat) > 0.5) {
                int s = lat > 0 ? 1 : -1;
                if (s != lastLatSign) { lastLatSign = s; lastLatFlip = now; }
            }
        }
        fh = fh();
        allRef = all;
        updateWaves(now);
        if (Math.abs(r.getVelocity()) < 0.5) stuck++; else stuck = 0;
        if (others <= 1 && target != null && target.lastSeen >= 0) duel(target, now);
        else if (target != null) melee(all, target, now);
    }

    private void updateWaves(long now) {
        double mx = r.getX(), my = r.getY();
        for (int i = waves.size() - 1; i >= 0; i--) {
            EnemyWave w = waves.get(i);
            double d = Util.dist(w.ox, w.oy, mx, my);
            double tr = w.traveled(now);
            if (!w.crossed && tr >= d) {
                w.crossed = true;
                w.crossGf = gfOf(w, mx, my);
            }
            if (tr > d + 40) {
                if (w.crossed) {
                    w.src.learnVisit(w.ctx, w.crossGf);
                    if (w.feat != null) w.src.surfKnn.add(w.feat, w.crossGf, 1.0);
                }
                waves.remove(i);
            }
        }
    }

    private static double gfOf(EnemyWave w, double x, double y) {
        return Util.lim(-1, Util.normRel(Util.angle(w.ox, w.oy, x, y) - w.directAngle) * w.dir / w.mea, 1);
    }

    // ---------------------------------------------------------------------------------------------
    // 1v1

    private double desiredDistance(Enemy t, long now) {
        if (t.energy <= 0.0001) return 110;
        boolean passive = now > 200 && now - Math.max(t.lastShotTime, 0) > 200;
        // escala com a menor dimensao da arena (430 px em 600; 288 px em 400)
        double scale = Math.min(1.0, Config.DIST_ARENA_FRAC * Math.min(fw, fh) / Config.DESIRED_DISTANCE);
        double base = passive ? Config.PASSIVE_DISTANCE : Config.DESIRED_DISTANCE;
        if (!passive && t.closeEma > Config.CLOSE_THRESH) base *= Config.CLOSE_SCALE;
        return Math.max(120, base * scale);
    }

    private void duel(Enemy t, long now) {
        double desired = desiredDistance(t, now);
        EnemyWave w1 = null, w2 = null;
        double t1 = 1e9, t2 = 1e9;
        double mx = r.getX(), my = r.getY();
        for (EnemyWave w : waves) {
            double rem = (Util.dist(w.ox, w.oy, mx, my) - 18 - w.traveled(now)) / w.speed;
            if (rem <= 0) continue;
            if (rem < t1) { w2 = w1; t2 = t1; w1 = w; t1 = rem; }
            else if (rem < t2) { w2 = w; t2 = rem; }
        }

        if (w1 == null || !Config.SURF) {
            if (now >= nextFlip) {
                orbitDir = -orbitDir;
                nextFlip = now + 12 + rnd.nextInt(40);
            }
            double ex = t.x, ey = t.y;
            int age = (int) (now - t.lastSeen);
            if (age > 0) { t.project(age, fw, fh, tmp); ex = tmp[0]; ey = tmp[1]; }
            applyReal(goAngle(mx, my, ex, ey, orbitDir, desired));
            return;
        }

        double dCW = evalOption(w1, w2, 1, now, desired);
        double dCCW = evalOption(w1, w2, -1, now, desired);
        double dST = Config.SURF_STOP ? evalOption(w1, w2, 0, now, desired) : Double.MAX_VALUE;
        double cur = orbitDir > 0 ? dCW : dCCW;
        double oth = orbitDir > 0 ? dCCW : dCW;
        if (Config.SURF_STOP && dST * Config.SURF_STOP_BIAS < Math.min(cur, oth) - 1e-6) {
            r.setMaxVelocity(0);                      // parar: espera a onda passar
            return;
        }
        if (oth < cur * (1 - Config.SURF_HYSTERESIS) - 1e-6) orbitDir = -orbitDir;
        applyReal(goAngle(mx, my, w1.ox, w1.oy, orbitDir, desired));
    }

    private final int[] kOut = new int[1];

    /** Perigo de uma opcao (+1 horario, -1 anti-horario, 0 parar) para w1 e, recursivamente, a melhor opcao para w2. */
    private double evalOption(EnemyWave w1, EnemyWave w2, int opt, long now, double desired) {
        double[] s = {r.getX(), r.getY(), r.getHeadingRadians(), r.getVelocity()};
        double d1 = simCross(s, w1, opt, now, desired);
        if (w2 == null) return d1;
        long t1 = now + kOut[0];
        double best = Double.MAX_VALUE;
        int[] opts = Config.SURF_RECURSE ? new int[]{1, -1, 0} : new int[]{opt};
        for (int o2 : opts) {
            if (o2 == 0 && !Config.SURF_STOP) continue;
            double[] s2 = {s[0], s[1], s[2], s[3]};
            best = Math.min(best, simCross(s2, w2, o2, t1, desired));
        }
        return d1 + Config.SURF_SECOND_WAVE * best;
    }

    /** Simula ate a onda w alcancar o robo; devolve o perigo no ponto de impacto e deixa em kOut[0] os ticks gastos. */
    private double simCross(double[] s, EnemyWave w, int opt, long t0, double desired) {
        for (int k = 1; k <= 130; k++) {
            if (opt == 0) {
                double v = s[3];
                v = v > 0 ? Math.max(0, v - 2) : Math.min(0, v + 2);
                s[3] = v;
                s[0] = Util.lim(Config.WALL_MARGIN, s[0] + Math.sin(s[2]) * v, fw - Config.WALL_MARGIN);
                s[1] = Util.lim(Config.WALL_MARGIN, s[1] + Math.cos(s[2]) * v, fh - Config.WALL_MARGIN);
            } else {
                step(s, goAngle(s[0], s[1], w.ox, w.oy, opt, desired));
            }
            double d = Util.dist(w.ox, w.oy, s[0], s[1]);
            if ((t0 + k - w.fireTime) * w.speed >= d - 18) {
                kOut[0] = k;
                double gf = gfOf(w, s[0], s[1]);
                double halfW = Math.atan(18.0 / Math.max(d, 30)) / w.mea;
                return w.src.danger(w.ctx, gf, halfW, w.gfLinear, w.pdf, w.pdfConf) * (0.4 + Util.bulletDamage(w.power) / 10.0);
            }
        }
        kOut[0] = 130;
        return 0;
    }

    /** Simula a orbita no sentido dir e devolve o perigo acumulado nos pontos de impacto previstos. */
    private double evalDir(EnemyWave w1, EnemyWave w2, int dir, long now, double desired) {
        sim[0] = r.getX(); sim[1] = r.getY(); sim[2] = r.getHeadingRadians(); sim[3] = r.getVelocity();
        EnemyWave cur = w1;
        boolean second = false;
        double total = 0;
        for (int k = 1; k <= 130; k++) {
            double go = goAngle(sim[0], sim[1], cur.ox, cur.oy, dir, desired);
            step(sim, go);
            long t = now + k;
            double d = Util.dist(cur.ox, cur.oy, sim[0], sim[1]);
            if ((t - cur.fireTime) * cur.speed >= d - 18) {
                double gf = gfOf(cur, sim[0], sim[1]);
                double halfW = Math.atan(18.0 / Math.max(d, 30)) / cur.mea;
                double dng = cur.src.danger(cur.ctx, gf, halfW, cur.gfLinear, cur.pdf, cur.pdfConf) * (0.4 + Util.bulletDamage(cur.power) / 10.0);
                if (!second) {
                    total += dng;
                    if (w2 == null) break;
                    cur = w2;
                    second = true;
                } else {
                    total += Config.SURF_SECOND_WAVE * dng;
                    break;
                }
            }
        }
        return total;
    }

    /** Direcao desejada: tangente da orbita em torno de (ox,oy) ajustada pela distancia, com wall smoothing. */
    private double goAngle(double x, double y, double ox, double oy, int dir, double desired) {
        double theta = Util.angle(ox, oy, x, y);
        double d = Util.dist(ox, oy, x, y);
        double off = Util.lim(-1.1, (d - desired) * 0.0035, 0.6);
        double a = theta + dir * (Util.HALF_PI + off);
        return wallSmooth(x, y, a, dir);
    }

    private double wallSmooth(double x, double y, double a, int dir) {
        double m = Config.WALL_MARGIN;
        double stick = Config.WALL_STICK;
        for (int i = 0; i < 70; i++) {
            double px = x + Math.sin(a) * stick, py = y + Math.cos(a) * stick;
            if (px >= m && px <= fw - m && py >= m && py <= fh - m) break;
            a += dir * 0.05;
        }
        return a;
    }

    /** Um tick da fisica de movimento do robo: s = {x, y, heading, velocity}. */
    private void step(double[] s, double go) {
        double ang = Util.normRel(go - s[2]);
        int md = 1;
        if (Math.abs(ang) > Util.HALF_PI) { md = -1; ang = Util.normRel(ang + Math.PI); }
        double mt = Util.maxTurn(s[3]);
        s[2] += Util.lim(-mt, ang, mt);
        double v = s[3];
        v += md * (v * md >= 0 ? 1 : 2);
        v = Util.lim(-8, v, 8);
        s[3] = v;
        s[0] += Math.sin(s[2]) * v;
        s[1] += Math.cos(s[2]) * v;
        double m = Config.WALL_MARGIN;
        if (s[0] < m || s[0] > fw - m || s[1] < m || s[1] > fh - m) {
            s[0] = Util.lim(m, s[0], fw - m);
            s[1] = Util.lim(m, s[1], fh - m);
            s[3] = 0;
        }
    }

    private void applyReal(double go) {
        double ang = Util.normRel(go - r.getHeadingRadians());
        int md = 1;
        if (Math.abs(ang) > Util.HALF_PI) { md = -1; ang = Util.normRel(ang + Math.PI); }
        r.setTurnRightRadians(ang);
        r.setMaxVelocity(8);
        r.setAhead(md * 150);
    }

    // ---------------------------------------------------------------------------------------------
    // Melee: risco minimo

    private void melee(Collection<Enemy> all, Enemy target, long now) {
        double x = r.getX(), y = r.getY();
        boolean reeval = now - goalTime >= 7 || Util.dist(x, y, goalX, goalY) < 14 || stuck > 6;
        if (reeval) {
            double bestRisk = Double.MAX_VALUE;
            double bx = x, by = y;
            double m = Config.WALL_MARGIN + 3;
            double[] radii = {70, 130, 200};
            for (int i = 0; i < 24; i++) {
                double a = i * Util.TWO_PI / 24;
                for (double rad : radii) {
                    double px = x + Math.sin(a) * rad, py = y + Math.cos(a) * rad;
                    if (px < m || px > fw - m || py < m || py > fh - m) continue;
                    double risk = riskAt(px, py, x, y, all, target, now);
                    double turn = Math.abs(Util.normRel(a - r.getHeadingRadians()));
                    if (turn > Util.HALF_PI) turn = Math.PI - turn;
                    risk *= 1 + 0.10 * turn;
                    if (risk < bestRisk) { bestRisk = risk; bx = px; by = py; }
                }
            }
            if (Config.MELEE_KEEP_DEST && now - goalTime < 40 && Util.dist(x, y, goalX, goalY) >= 14) {
                double rk = riskAt(goalX, goalY, x, y, all, target, now) * 0.8;
                if (rk < bestRisk) { bestRisk = rk; bx = goalX; by = goalY; }
            }
            goalX = bx; goalY = by; goalTime = now;
            stuck = 0;
        }
        applyReal(Util.angle(x, y, goalX, goalY));
    }

    private double riskAt(double px, double py, double x, double y, Collection<Enemy> all, Enemy target, long now) {
        double risk = 0;
        double mvAng = Util.angle(x, y, px, py);
        double m = Config.WALL_MARGIN;
        for (Enemy e : all) {
            if (!e.alive || e.lastSeen < 0) continue;
            int age = (int) (now - e.lastSeen);
            double ex = e.x, ey = e.y;
            if (age > 0) { e.project(Math.min(age, 25), fw, fh, proj); ex = proj[0]; ey = proj[1]; }
            double dx = px - ex, dy = py - ey;
            double d2 = dx * dx + dy * dy + 400;
            double toEnemy = Util.angle(x, y, ex, ey);
            double c = Math.abs(Math.cos(Util.normRel(mvAng - toEnemy)));
            double base = Config.MELEE_ENERGY_RATIO ? 100.0 * Util.lim(0.25, e.energy / Math.max(r.getEnergy(), 1), 4) : (e.energy + 30);
            double w = base / d2 * (1 + c);
            if (Config.MELEE_CROWD) {
                double de = Math.sqrt(d2), lim = 0.8 * de;
                int closer = 0;
                for (Enemy o : all) {
                    if (o == e || !o.alive || o.lastSeen < 0) continue;
                    if (Util.dist(px, py, o.x, o.y) < lim) closer++;
                }
                w /= (closer + 1);
            }
            if (age > 30) w *= 0.5;
            risk += w;
            double d = Math.sqrt(d2);
            if (d < Config.MELEE_MIN_SAFE_DIST) risk += (Config.MELEE_MIN_SAFE_DIST - d) / Config.MELEE_MIN_SAFE_DIST * 0.01;
            if (e == target) {
                double dd = d - Config.MELEE_PREF_DIST;
                risk += Config.MELEE_ATTRACT * dd * dd;
            }
        }
        // paredes e cantos
        double dw = Math.min(Math.min(px - m, fw - m - px), Math.min(py - m, fh - m - py));
        if (dw < 100) {
            double f = 1 - dw / 100;
            risk += Config.MELEE_WALL_RISK * f * f * 3;
        }
        double cx = Math.min(px, fw - px), cy = Math.min(py, fh - py);
        double dc = Math.sqrt(cx * cx + cy * cy);
        if (dc < 170) risk += 0.0012 * (1 - dc / 170);

        // ondas inimigas estimadas (podem mirar em outro alvo: peso reduzido)
        double mvx = px - x, mvy = py - y;
        double mvLen = Math.sqrt(mvx * mvx + mvy * mvy);
        double ux = mvLen > 1e-6 ? mvx / mvLen : 0, uy = mvLen > 1e-6 ? mvy / mvLen : 0;
        for (EnemyWave w : waves) {
            double rad = w.traveled(now);
            double remaining = Util.dist(w.ox, w.oy, x, y) - rad;
            if (remaining <= -20) continue;
            double tArr = Math.max(1.0, remaining / w.speed);
            if (tArr > 60) continue;
            // onde estaremos quando a onda chegar, indo em linha reta ate o candidato
            double travel = Math.min(mvLen, 6.5 * tArr);
            double ax = x + ux * travel, ay = y + uy * travel;
            double dd = Math.max(30, Util.dist(w.ox, w.oy, ax, ay));
            double gf = gfOf(w, ax, ay);
            double halfW = Math.atan(18.0 / dd) / w.mea;
            double dng = w.src.danger(w.ctx, gf, halfW, w.gfLinear, w.pdf, w.pdfConf);
            risk += Config.MELEE_WAVE_K * w.weight * dng * (0.4 + Util.bulletDamage(w.power) / 10.0) / (1.0 + tArr / 30.0);
        }
        return risk;
    }
}
