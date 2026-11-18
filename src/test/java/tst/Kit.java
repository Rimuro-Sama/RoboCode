package tst;

import java.util.ArrayList;
import robocode.AdvancedRobot;
import robocode.util.Utils;

/** Ferramentas compartilhadas pelos adversarios fortes de teste. */
final class Kit {
    static final int B = 31;

    private Kit() {}

    static double nr(double a) { return Utils.normalRelativeAngle(a); }
    static double lim(double lo, double v, double hi) { return v < lo ? lo : (v > hi ? hi : v); }
    static double bs(double p) { return 20 - 3 * p; }

    static double smooth(double x, double y, double a, int dir, double fw, double fh) {
        for (int i = 0; i < 70; i++) {
            double px = x + Math.sin(a) * 130, py = y + Math.cos(a) * 130;
            if (px > 18 && px < fw - 18 && py > 18 && py < fh - 18) break;
            a += dir * 0.05;
        }
        return a;
    }

    static void drive(AdvancedRobot r, double go) {
        double ang = nr(go - r.getHeadingRadians());
        int md = 1;
        if (Math.abs(ang) > Math.PI / 2) { md = -1; ang = nr(ang + Math.PI); }
        r.setTurnRightRadians(ang);
        r.setAhead(md * 150);
    }

    static void lock(AdvancedRobot r, double abs) {
        r.setTurnRadarRightRadians(nr(abs - r.getRadarHeadingRadians()) * 2.0);
    }

    /** Aim iterativo com rumo/velocidade constantes e giro opcional. */
    static double predict(double mx, double my, double ex, double ey, double eh, double ev, double turn,
                          double speed, double fw, double fh) {
        double px = ex, py = ey, h = eh, v = ev;
        for (int k = 1; k < 150; k++) {
            h += turn;
            px += Math.sin(h) * v;
            py += Math.cos(h) * v;
            if (px < 18 || px > fw - 18 || py < 18 || py > fh - 18) {
                px = lim(18, px, fw - 18);
                py = lim(18, py, fh - 18);
                v = 0; turn = 0;
            }
            if (k * speed >= Math.hypot(px - mx, py - my)) break;
        }
        return Math.atan2(px - mx, py - my);
    }

    // ------------------------------------------------------------------ GuessFactor gun
    static final class GfGun {
        static final class W { double ox, oy, sp, ref, mea; int dir; long t; int seg; }
        final double[][] st = new double[48][B];
        final ArrayList<W> ws = new ArrayList<W>();
        final double decay;
        private double pRef, pMea; private int pDir, pSeg;

        GfGun(double decay) { this.decay = decay; }

        double aim(double mx, double my, double ex, double ey, double eh, double ev, double power) {
            double abs = Math.atan2(ex - mx, ey - my);
            double d = Math.hypot(ex - mx, ey - my);
            double lat = ev * Math.sin(eh - abs);
            double adv = -ev * Math.cos(eh - abs);
            pDir = lat >= 0 ? 1 : -1;
            double al = Math.abs(lat);
            pSeg = ((d < 250 ? 0 : d < 450 ? 1 : d < 650 ? 2 : 3) * 4 + (al < 1 ? 0 : al < 3 ? 1 : al < 6 ? 2 : 3)) * 3
                    + (adv < -2 ? 0 : adv > 2 ? 2 : 1);
            pMea = Math.asin(8 / bs(power));
            pRef = abs;
            double[] s = st[pSeg];
            int best = B / 2;
            for (int i = 0; i < B; i++) if (s[i] > s[best]) best = i;
            return abs + pDir * (best * 2.0 / (B - 1) - 1) * pMea;
        }

        void shot(long now, double mx, double my, double power) {
            W w = new W();
            w.ox = mx; w.oy = my; w.sp = bs(power); w.ref = pRef; w.mea = pMea; w.dir = pDir; w.t = now; w.seg = pSeg;
            ws.add(w);
        }

        void track(long now, double ex, double ey) {
            for (int i = ws.size() - 1; i >= 0; i--) {
                W w = ws.get(i);
                if ((now - w.t) * w.sp >= Math.hypot(ex - w.ox, ey - w.oy)) {
                    double a = Math.atan2(ex - w.ox, ey - w.oy);
                    double gf = lim(-1, nr(a - w.ref) * w.dir / w.mea, 1);
                    int c = (int) Math.round((gf + 1) / 2 * (B - 1));
                    double[] s = st[w.seg];
                    for (int k = 0; k < B; k++) s[k] = s[k] * decay + Math.exp(-0.5 * (k - c) * (k - c) / 2.0);
                    ws.remove(i);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Wave surfing
    static final class Surf {
        static final class EW { double ox, oy, sp, ref; int dir; long t; }
        final double[] hit = new double[B];
        final double[] vis = new double[B];
        final ArrayList<EW> ws = new ArrayList<EW>();
        double lastE = 100, adjust;
        double px, py, ph, pv;           // nosso estado no scan anterior
        double lastX, lastY;             // posicao do inimigo no scan anterior
        int dir = 1;
        final double visW;

        Surf(double visW) { this.visW = visW; }

        void myHit(double power) { adjust += 4 * power + (power > 1 ? 2 * (power - 1) : 0); }
        void hitMe(double power) { adjust -= 3 * power; }

        /** Chamar a cada scan, ANTES de mover. */
        void scan(AdvancedRobot r, double ex, double ey, double eEnergy, long now) {
            double drop = lastE - eEnergy - adjust;
            adjust = 0;
            if (drop > 0.09 && drop < 3.01 && now > 1) {
                EW w = new EW();
                w.ox = lastX; w.oy = lastY; w.sp = bs(drop); w.t = now - 1;
                w.ref = Math.atan2(px - lastX, py - lastY);
                double lat = pv * Math.sin(ph - w.ref);
                w.dir = lat >= 0 ? 1 : -1;
                ws.add(w);
            }
            lastE = eEnergy; lastX = ex; lastY = ey;
            // ondas passadas -> visitas
            double mx = r.getX(), my = r.getY();
            for (int i = ws.size() - 1; i >= 0; i--) {
                EW w = ws.get(i);
                if ((now - w.t) * w.sp > Math.hypot(mx - w.ox, my - w.oy) + 30) {
                    add(vis, gf(w, mx, my, Math.asin(8 / w.sp)), 1);
                    ws.remove(i);
                }
            }
        }

        void onHit(AdvancedRobot r, double power, long now) {
            double mx = r.getX(), my = r.getY();
            EW best = null; double bd = 80;
            for (EW w : ws) {
                if (Math.abs(w.sp - bs(power)) > 0.2) continue;
                double dd = Math.abs((now - w.t) * w.sp - Math.hypot(mx - w.ox, my - w.oy));
                if (dd < bd) { bd = dd; best = w; }
            }
            if (best != null) { add(hit, gf(best, mx, my, Math.asin(8 / best.sp)), 3); ws.remove(best); }
        }

        void save(AdvancedRobot r) { px = r.getX(); py = r.getY(); ph = r.getHeadingRadians(); pv = r.getVelocity(); }

        static double gf(EW w, double x, double y, double mea) {
            return lim(-1, nr(Math.atan2(x - w.ox, y - w.oy) - w.ref) * w.dir / mea, 1);
        }

        static void add(double[] a, double gf, double w) {
            int c = (int) Math.round((gf + 1) / 2 * (B - 1));
            for (int k = 0; k < B; k++) a[k] = a[k] * 0.99 + w * Math.exp(-0.5 * (k - c) * (k - c) / 3.0);
        }

        double danger(EW w, double x, double y) {
            double mea = Math.asin(8 / w.sp);
            int c = (int) Math.round((gf(w, x, y, mea) + 1) / 2 * (B - 1));
            double s = 0;
            for (int k = Math.max(0, c - 2); k <= Math.min(B - 1, c + 2); k++) s += hit[k] + visW * vis[k];
            return s + 0.3 * Math.exp(-0.5 * (c - B / 2) * (c - B / 2) / 6.0);   // prior head-on
        }

        double sim(AdvancedRobot r, EW w, int d, long now, double ex, double ey, double want, double fw, double fh) {
            double x = r.getX(), y = r.getY(), h = r.getHeadingRadians(), v = r.getVelocity();
            for (int k = 1; k < 120; k++) {
                double th = Math.atan2(x - w.ox, y - w.oy), dist = Math.hypot(x - w.ox, y - w.oy);
                double a = smooth(x, y, th + d * (Math.PI / 2 + lim(-1, (dist - want) * 0.003, 0.6)), d, fw, fh);
                double ang = nr(a - h); int md = 1;
                if (Math.abs(ang) > Math.PI / 2) { md = -1; ang = nr(ang + Math.PI); }
                double mt = Math.toRadians(10 - 0.75 * Math.abs(v));
                h += lim(-mt, ang, mt);
                v += md * (v * md >= 0 ? 1 : 2); v = lim(-8, v, 8);
                x = lim(18, x + Math.sin(h) * v, fw - 18); y = lim(18, y + Math.cos(h) * v, fh - 18);
                if ((now + k - w.t) * w.sp >= Math.hypot(x - w.ox, y - w.oy) - 18) return danger(w, x, y);
            }
            return 0;
        }

        void move(AdvancedRobot r, double ex, double ey, double want, long now) {
            double fw = r.getBattleFieldWidth(), fh = r.getBattleFieldHeight();
            EW best = null; double bt = 1e9;
            for (EW w : ws) {
                double t = (Math.hypot(r.getX() - w.ox, r.getY() - w.oy) - 18 - (now - w.t) * w.sp) / w.sp;
                if (t > 0 && t < bt) { bt = t; best = w; }
            }
            double ox = ex, oy = ey;
            if (best != null) {
                double a = sim(r, best, 1, now, ex, ey, want, fw, fh), b = sim(r, best, -1, now, ex, ey, want, fw, fh);
                if ((dir > 0 ? b : a) < (dir > 0 ? a : b) * 0.97) dir = -dir;
                ox = best.ox; oy = best.oy;
            }
            double th = Math.atan2(r.getX() - ox, r.getY() - oy), dist = Math.hypot(r.getX() - ox, r.getY() - oy);
            double go = th + dir * (Math.PI / 2 + lim(-1, (dist - want) * 0.003, 0.6));
            drive(r, smooth(r.getX(), r.getY(), go, dir, fw, fh));
        }
    }
}
