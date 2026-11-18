package tst;

import robocode.*;

/** Teste: movimento circular constante, mira direta (head-on). */
public class Circler extends AdvancedRobot {
    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setMaxVelocity(6);
        while (true) {
            setTurnRight(4);
            setAhead(100);
            if (getRadarTurnRemaining() == 0) setTurnRadarRight(360);
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double abs = getHeadingRadians() + e.getBearingRadians();
        setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(abs - getGunHeadingRadians()));
        double p = e.getDistance() < 300 ? 2.5 : 1.5;
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 8) setFire(p);
        setTurnRadarRightRadians(robocode.util.Utils.normalRelativeAngle(abs - getRadarHeadingRadians()) * 2);
    }

    public void onHitWall(HitWallEvent e) {
        setBack(60);
    }
}
