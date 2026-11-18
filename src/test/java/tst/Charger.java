package tst;

import robocode.*;

/** Forte #3: agressivo - avanca ate curta distancia, potencia 3 com mira linear, ziguezague e abalroa se o alvo esta fraco. */
public class Charger extends AdvancedRobot {
    private int zig = 1;
    private long next;

    public void run() {
        setAdjustGunForRobotTurn(true); setAdjustRadarForGunTurn(true); setAdjustRadarForRobotTurn(true);
        while (true) {
            if (getOthers() > 1 || getRadarTurnRemaining() == 0) setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double abs = getHeadingRadians() + e.getBearingRadians();
        double d = e.getDistance();
        double ex = getX() + Math.sin(abs) * d, ey = getY() + Math.cos(abs) * d;
        if (getTime() > next) { zig = -zig; next = getTime() + 6 + (long) (Math.random() * 14); }
        boolean ram = e.getEnergy() < 20 || e.getEnergy() < getEnergy() * 0.3;
        double off = ram ? 0 : zig * 0.5 * Math.min(1, d / 200);
        Kit.drive(this, Kit.smooth(getX(), getY(), abs + off, zig, getBattleFieldWidth(), getBattleFieldHeight()));

        double p = d < 300 ? 3 : 2;
        if (getEnergy() < 15) p = 1;
        double aim = Kit.predict(getX(), getY(), ex, ey, e.getHeadingRadians(), e.getVelocity(), 0, Kit.bs(p),
                getBattleFieldWidth(), getBattleFieldHeight());
        setTurnGunRightRadians(Kit.nr(aim - getGunHeadingRadians()));
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 5 && getEnergy() > p + 0.1) setFire(p);
        if (getOthers() == 1) Kit.lock(this, abs);
    }

    public void onHitWall(HitWallEvent e) { zig = -zig; }
}
