package tst;

import java.util.HashMap;
import robocode.*;

/** Forte #5 (bom em melee): anti-gravidade (inimigos + paredes + forca orbital), alvo mais fraco/proximo, mira circular. */
public class GravBot extends AdvancedRobot {
    private static class E { double x, y, energy, h, v, turn, lastH; long t; boolean alive = true; }
    private final HashMap<String, E> es = new HashMap<String, E>();
    private String target;
    private int dir = 1;
    private long next;

    public void run() {
        setAdjustGunForRobotTurn(true); setAdjustRadarForGunTurn(true); setAdjustRadarForRobotTurn(true);
        es.clear();
        while (true) {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            move();
            execute();
        }
    }

    private void move() {
        double fx = 0, fy = 0, x = getX(), y = getY(), fw = getBattleFieldWidth(), fh = getBattleFieldHeight();
        for (E e : es.values()) {
            if (!e.alive) continue;
            double d = Math.max(30, Math.hypot(x - e.x, y - e.y));
            double a = Math.atan2(x - e.x, y - e.y);
            double f = (e.energy + 60) / (d * d) * 8000;
            fx += Math.sin(a) * f; fy += Math.cos(a) * f;
            // forca orbital
            double o = 0.5 * f * dir;
            fx += Math.sin(a + Math.PI / 2) * o; fy += Math.cos(a + Math.PI / 2) * o;
        }
        double w = 4000;
        fx += w / (x * x) - w / ((fw - x) * (fw - x));
        fy += w / (y * y) - w / ((fh - y) * (fh - y));
        if (getTime() > next) { dir = -dir; next = getTime() + 20 + (long) (Math.random() * 40); }
        Kit.drive(this, Math.atan2(fx, fy));
    }

    public void onScannedRobot(ScannedRobotEvent s) {
        E e = es.get(s.getName());
        if (e == null) { e = new E(); es.put(s.getName(), e); }
        double abs = getHeadingRadians() + s.getBearingRadians();
        e.x = getX() + Math.sin(abs) * s.getDistance(); e.y = getY() + Math.cos(abs) * s.getDistance();
        e.energy = s.getEnergy(); e.h = s.getHeadingRadians(); e.v = s.getVelocity();
        e.turn = 0.5 * e.turn + 0.5 * Kit.nr(e.h - e.lastH); e.lastH = e.h; e.t = getTime(); e.alive = true;

        E t = target == null ? null : es.get(target);
        double bestScore = Double.MAX_VALUE;
        if (t == null || !t.alive) target = null;
        for (java.util.Map.Entry<String, E> en : es.entrySet()) {
            E c = en.getValue();
            if (!c.alive) continue;
            double sc = Math.hypot(c.x - getX(), c.y - getY()) + c.energy * 2;
            if (sc < bestScore) { bestScore = sc; target = en.getKey(); }
        }
        if (!s.getName().equals(target)) return;
        double d = s.getDistance();
        double p = d < 200 ? 3 : d < 450 ? 2 : 1.2;
        if (getEnergy() < 20) p = Math.min(p, 1);
        double aim = Kit.predict(getX(), getY(), e.x, e.y, e.h, e.v, e.turn, Kit.bs(p), getBattleFieldWidth(), getBattleFieldHeight());
        setTurnGunRightRadians(Kit.nr(aim - getGunHeadingRadians()));
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 4 && getEnergy() > p + 0.1) setFire(p);
    }

    public void onRobotDeath(RobotDeathEvent e) {
        E x = es.get(e.getName());
        if (x != null) x.alive = false;
    }

    public void onHitWall(HitWallEvent e) { dir = -dir; }
}
