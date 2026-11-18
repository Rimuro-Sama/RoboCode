package tst;

import robocode.*;
import robocode.util.Utils;

/** Teste: defensivo/longa distancia - vai para um canto, fica parado e atira potencia 1 (head-on). */
public class Camper extends AdvancedRobot {
    private boolean arrived;

    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        // vai para o canto inferior-esquerdo
        double a = Math.atan2(40 - getX(), 40 - getY());
        setTurnRightRadians(Utils.normalRelativeAngle(a - getHeadingRadians()));
        turnRightRadians(Utils.normalRelativeAngle(a - getHeadingRadians()));
        ahead(Math.hypot(40 - getX(), 40 - getY()));
        arrived = true;
        while (true) {
            if (getRadarTurnRemaining() == 0) setTurnRadarRight(Double.POSITIVE_INFINITY);
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        if (!arrived) return;
        double abs = getHeadingRadians() + e.getBearingRadians();
        setTurnGunRightRadians(Utils.normalRelativeAngle(abs - getGunHeadingRadians()));
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 3) setFire(1.0);
        setTurnRadarRightRadians(Utils.normalRelativeAngle(abs - getRadarHeadingRadians()) * 2.0);
    }
}
