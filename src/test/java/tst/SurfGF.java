package tst;

import robocode.*;

/** Forte #1: wave surfing (acertos + visitas) + mira GuessFactor segmentada. */
public class SurfGF extends AdvancedRobot {
    private final Kit.Surf surf = new Kit.Surf(0.05);
    private static Kit.GfGun gun = new Kit.GfGun(0.98);

    public void run() {
        setAdjustGunForRobotTurn(true); setAdjustRadarForGunTurn(true); setAdjustRadarForRobotTurn(true);
        while (true) {
            if (getOthers() > 1 || getRadarTurnRemaining() == 0) setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double abs = getHeadingRadians() + e.getBearingRadians();
        double ex = getX() + Math.sin(abs) * e.getDistance(), ey = getY() + Math.cos(abs) * e.getDistance();
        long now = getTime();
        surf.scan(this, ex, ey, e.getEnergy(), now);
        surf.move(this, ex, ey, 420, now);
        surf.save(this);

        double p = Math.min(3, Math.max(0.5, 1000 / e.getDistance() - 1));
        if (getEnergy() < 25) p = Math.min(p, 1.2);
        gun.track(now, ex, ey);
        double aim = gun.aim(getX(), getY(), ex, ey, e.getHeadingRadians(), e.getVelocity(), p);
        setTurnGunRightRadians(Kit.nr(aim - getGunHeadingRadians()));
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 3 && getEnergy() > p + 0.1) {
            setFire(p);
            gun.shot(now, getX(), getY(), p);
        }
        if (getOthers() == 1) Kit.lock(this, abs);
    }

    public void onHitByBullet(HitByBulletEvent e) { surf.hitMe(e.getPower()); surf.onHit(this, e.getPower(), getTime()); }
    public void onBulletHit(BulletHitEvent e) { surf.myHit(e.getBullet().getPower()); }
    public void onHitWall(HitWallEvent e) { surf.dir = -surf.dir; }
}
