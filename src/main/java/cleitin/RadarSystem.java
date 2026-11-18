package cleitin;

import java.util.Collection;

/**
 * RadarSystem.
 *  - 1v1: "lock" estreito com sobre-giro (nunca perde o alvo se ele andar < 8 px/tick).
 *  - melee: varredura continua; quando o dado do alvo envelhece, o radar vai ate ele e depois retoma
 *    a varredura no sentido do inimigo mais "velho" (menos recentemente escaneado).
 */
final class RadarSystem {
    private final Cleitin_O_BomDeGuerra r;
    private int sweepDir = 1;
    private final double[] tmp = new double[4];

    RadarSystem(Cleitin_O_BomDeGuerra r) {
        this.r = r;
    }

    void tick(Enemy target, Collection<Enemy> all, int others, long now) {
        double fw = r.getBattleFieldWidth(), fh = r.getBattleFieldHeight();
        int seen = 0;
        for (Enemy e : all) if (e.alive && e.lastSeen >= 0) seen++;

        if (target == null || target.lastSeen < 0) {
            r.setTurnRadarRightRadians(sweepDir * Double.POSITIVE_INFINITY);
            return;
        }

        int age = (int) (now - target.lastSeen);
        boolean duel = others <= 1;

        if (duel || (others == 2 && allFresh(all, now, 12))) {
            if (age <= 3) {
                lockOn(target, age, fw, fh);
            } else {
                r.setTurnRadarRightRadians(sweepDir * Double.POSITIVE_INFINITY);
            }
            return;
        }

        // melee: varredura em arco (so o setor onde estao os inimigos), indo e voltando
        if (Config.RADAR_ARC && seen >= 2 && seen >= others && arcSweep(all, seen, now)) return;

        // melee
        boolean needAll = seen < others;
        if (!needAll && age >= 3 && age <= 12) {
            lockOn(target, age, fw, fh);       // atualiza o alvo, depois volta a varrer
            return;
        }
        // varredura no sentido do inimigo mais antigo
        Enemy stale = null;
        for (Enemy e : all) {
            if (!e.alive || e.lastSeen < 0) continue;
            if (stale == null || e.lastSeen < stale.lastSeen) stale = e;
        }
        if (stale != null && !needAll) {
            double ang = Util.angle(r.getX(), r.getY(), stale.x, stale.y);
            double diff = Util.normRel(ang - r.getRadarHeadingRadians());
            sweepDir = diff >= 0 ? 1 : -1;
        }
        r.setTurnRadarRightRadians(sweepDir * Double.POSITIVE_INFINITY);
    }

    private int arcDir = 1;

    /** Varre apenas o arco que cobre todos os inimigos (complemento do maior vao entre eles). false = girar tudo. */
    private boolean arcSweep(Collection<Enemy> all, int n, long now) {
        double mx = r.getX(), my = r.getY();
        double[] b = new double[n];
        int i = 0;
        double fw = r.getBattleFieldWidth(), fh = r.getBattleFieldHeight();
        for (Enemy e : all) {
            if (!e.alive || e.lastSeen < 0 || i >= n) continue;
            double ex = e.x, ey = e.y;
            int age = (int) (now - e.lastSeen);
            if (age > 0) { e.project(Math.min(age, 25), fw, fh, tmp); ex = tmp[0]; ey = tmp[1]; }
            b[i++] = normAbs(Util.angle(mx, my, ex, ey));
        }
        java.util.Arrays.sort(b, 0, i);
        int gi = 0;
        double maxGap = -1;
        for (int k = 0; k < i; k++) {
            double next = (k == i - 1) ? b[0] + Util.TWO_PI : b[k + 1];
            if (next - b[k] > maxGap) { maxGap = next - b[k]; gi = k; }
        }
        double arcWidth = Util.TWO_PI - maxGap;
        if (arcWidth > Config.RADAR_ARC_MAX) return false;
        double center = normAbs(b[(gi + 1) % i] + arcWidth / 2);
        double half = arcWidth / 2 + Config.RADAR_ARC_PAD;
        double rel = Util.normRel(r.getRadarHeadingRadians() - center);
        if (rel > half) arcDir = -1;
        else if (rel < -half) arcDir = 1;
        r.setTurnRadarRightRadians(arcDir * Math.PI);
        return true;
    }

    private static double normAbs(double a) {
        a %= Util.TWO_PI;
        return a < 0 ? a + Util.TWO_PI : a;
    }

    private boolean allFresh(Collection<Enemy> all, long now, int maxAge) {
        for (Enemy e : all) if (e.alive && now - e.lastSeen > maxAge) return false;
        return true;
    }

    private void lockOn(Enemy t, int age, double fw, double fh) {
        double ex = t.x, ey = t.y;
        if (age > 0) {
            t.project(age, fw, fh, tmp);
            ex = tmp[0]; ey = tmp[1];
        }
        double d = Math.max(Util.dist(r.getX(), r.getY(), ex, ey), 40);
        double abs = Util.angle(r.getX(), r.getY(), ex, ey);
        double turn = Util.normRel(abs - r.getRadarHeadingRadians());
        double extra = Math.min(Math.PI / 4, Math.atan(30.0 / d) + 0.02 * (age + 1));
        if (Math.abs(turn) < 1e-6) turn = sweepDir * 1e-6;
        sweepDir = turn >= 0 ? 1 : -1;
        r.setTurnRadarRightRadians(turn + sweepDir * extra);
    }
}
