package tst;

import java.util.ArrayList;
import java.util.Random;
import robocode.*;
import robocode.util.Utils;

/**
 * Teste (forte): movimento de orbita com inversoes aleatorias + GuessFactor targeting simples
 * (segmentado por distancia e velocidade lateral). Serve para medir o quanto o surf do Apex reduz os acertos.
 */
public class GFBot extends AdvancedRobot {
    static final int BINS = 47;
    static double[][] stats = new double[9][BINS];

    static class W { double ox, oy, speed, ref, mea; int dir; long t; int seg; }
    private final ArrayList<W> waves = new ArrayList<W>();
    private final Random rnd = new Random();
    private int mdir = 1;
    private long nextFlip;
    private double ex, ey;

    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        waves.clear();
        while (true) {
            if (getRadarTurnRemaining() == 0) setTurnRadarRight(Double.POSITIVE_INFINITY);
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double abs = getHeadingRadians() + e.getBearingRadians();
        double d = e.getDistance();
        ex = getX() + Math.sin(abs) * d;
        ey = getY() + Math.cos(abs) * d;

        // movimento
        if (getTime() >= nextFlip) { mdir = -mdir; nextFlip = getTime() + 15 + rnd.nextInt(40); }
        double go = abs + Math.PI / 2 * mdir - (d - 400) * 0.003 * mdir;
        for (int i = 0; i < 60; i++) {
            double px = getX() + Math.sin(go) * 120, py = getY() + Math.cos(go) * 120;
            if (px > 25 && px < getBattleFieldWidth() - 25 && py > 25 && py < getBattleFieldHeight() - 25) break;
            go += mdir * 0.06;
        }
        double ang = Utils.normalRelativeAngle(go - getHeadingRadians());
        int md = 1;
        if (Math.abs(ang) > Math.PI / 2) { md = -1; ang = Utils.normalRelativeAngle(ang + Math.PI); }
        setTurnRightRadians(ang);
        setAhead(md * 100);

        // resolve ondas
        for (int i = waves.size() - 1; i >= 0; i--) {
            W w = waves.get(i);
            if ((getTime() - w.t) * w.speed >= Math.hypot(ex - w.ox, ey - w.oy)) {
                double a = Math.atan2(ex - w.ox, ey - w.oy);
                double gf = Math.max(-1, Math.min(1, Utils.normalRelativeAngle(a - w.ref) * w.dir / w.mea));
                int b = (int) Math.round((gf + 1) / 2 * (BINS - 1));
                for (int k = 0; k < BINS; k++) stats[w.seg][k] += Math.exp(-0.5 * (k - b) * (k - b) / 2.0);
                waves.remove(i);
            }
        }

        // mira GF
        double p = Math.min(3, Math.max(0.8, 1000 / d - 1.0));
        if (getEnergy() < 20) p = Math.min(p, 1.0);
        double bs = 20 - 3 * p;
        double lat = e.getVelocity() * Math.sin(e.getHeadingRadians() - abs);
        int dir = lat >= 0 ? 1 : -1;
        int seg = (d < 300 ? 0 : d < 500 ? 1 : 2) * 3 + (Math.abs(lat) < 1 ? 0 : Math.abs(lat) < 5 ? 1 : 2);
        int best = BINS / 2;
        for (int k = 0; k < BINS; k++) if (stats[seg][k] > stats[seg][best]) best = k;
        double gf = best * 2.0 / (BINS - 1) - 1;
        double mea = Math.asin(8 / bs);
        double aim = abs + dir * gf * mea;
        setTurnGunRightRadians(Utils.normalRelativeAngle(aim - getGunHeadingRadians()));
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 3 && getEnergy() > p + 0.1) {
            setFire(p);
            W w = new W();
            w.ox = getX(); w.oy = getY(); w.speed = bs; w.ref = abs; w.mea = mea; w.dir = dir; w.t = getTime(); w.seg = seg;
            waves.add(w);
        }
        setTurnRadarRightRadians(Utils.normalRelativeAngle(abs - getRadarHeadingRadians()) * 2.0);
    }

    public void onHitWall(HitWallEvent e) {
        mdir = -mdir;
    }
}
