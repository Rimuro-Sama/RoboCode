package tst;

import java.util.Random;
import robocode.*;

/** Forte #2: esquiva por queda de energia (inverte sentido/velocidade ao detectar tiro) + mira circular. */
public class DropDodger extends AdvancedRobot {
    private final Random rnd = new Random();
    private double lastE = 100, adjust;
    private int dir = 1;

    public void run() {
        setAdjustGunForRobotTurn(true); setAdjustRadarForGunTurn(true); setAdjustRadarForRobotTurn(true);
        while (true) {
            if (getOthers() > 1 || getRadarTurnRemaining() == 0) setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            execute();
        }
    }

    private double lastH, turn;

    public void onScannedRobot(ScannedRobotEvent e) {
        double abs = getHeadingRadians() + e.getBearingRadians();
        double d = e.getDistance();
        double ex = getX() + Math.sin(abs) * d, ey = getY() + Math.cos(abs) * d;
        double drop = lastE - e.getEnergy() - adjust;
        adjust = 0; lastE = e.getEnergy();
        if (drop > 0.09 && drop < 3.01) {
            if (rnd.nextInt(3) != 0) dir = -dir;
            setMaxVelocity(rnd.nextInt(3) == 0 ? 3 : 8);
        } else if (rnd.nextInt(40) == 0) dir = -dir;
        double go = abs + dir * (Math.PI / 2 + Kit.lim(-1, (d - 380) * 0.003, 0.6));
        Kit.drive(this, Kit.smooth(getX(), getY(), go, dir, getBattleFieldWidth(), getBattleFieldHeight()));

        turn = 0.5 * turn + 0.5 * Kit.nr(e.getHeadingRadians() - lastH);
        lastH = e.getHeadingRadians();
        double p = d < 200 ? 3 : d < 450 ? 2 : 1.3;
        if (getEnergy() < 20) p = Math.min(p, 1.0);
        double aim = Kit.predict(getX(), getY(), ex, ey, e.getHeadingRadians(), e.getVelocity(), turn, Kit.bs(p),
                getBattleFieldWidth(), getBattleFieldHeight());
        setTurnGunRightRadians(Kit.nr(aim - getGunHeadingRadians()));
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 3 && getEnergy() > p + 0.1) setFire(p);
        if (getOthers() == 1) Kit.lock(this, abs);
    }

    public void onHitByBullet(HitByBulletEvent e) { adjust -= 3 * e.getPower(); }
    public void onBulletHit(BulletHitEvent e) { double p = e.getBullet().getPower(); adjust += 4 * p + (p > 1 ? 2 * (p - 1) : 0); }
    public void onHitWall(HitWallEvent e) { dir = -dir; }
}
