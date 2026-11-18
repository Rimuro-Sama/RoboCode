package tst;

import java.util.Random;
import robocode.*;
import robocode.util.Utils;

/** Teste: movimento imprevisivel (orbita com inversoes e paradas aleatorias) + mira preditiva linear. */
public class Strafer extends AdvancedRobot {
    private final Random rnd = new Random();
    private int dir = 1;
    private long nextFlip;
    private double lastEnergy = 100;

    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        while (true) {
            if (getRadarTurnRemaining() == 0) setTurnRadarRight(Double.POSITIVE_INFINITY);
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double abs = getHeadingRadians() + e.getBearingRadians();
        double d = e.getDistance();

        if (getTime() >= nextFlip) {
            dir = -dir;
            nextFlip = getTime() + 8 + rnd.nextInt(30);
            setMaxVelocity(rnd.nextInt(4) == 0 ? 0.0 : 4 + rnd.nextInt(5));
        }
        double go = abs + Math.PI / 2 * dir - (d - 350) * 0.003 * dir;
        // wall smoothing simples
        for (int i = 0; i < 60; i++) {
            double px = getX() + Math.sin(go) * 120, py = getY() + Math.cos(go) * 120;
            if (px > 25 && px < getBattleFieldWidth() - 25 && py > 25 && py < getBattleFieldHeight() - 25) break;
            go += dir * 0.06;
        }
        double ang = Utils.normalRelativeAngle(go - getHeadingRadians());
        int md = 1;
        if (Math.abs(ang) > Math.PI / 2) { md = -1; ang = Utils.normalRelativeAngle(ang + Math.PI); }
        setTurnRightRadians(ang);
        setAhead(md * 100);

        // linear targeting
        double p = Math.min(3, Math.max(0.5, 900 / d - 1.0));
        if (getEnergy() < 20) p = Math.min(p, 1.0);
        double bs = 20 - 3 * p;
        double ev = e.getVelocity(), eh = e.getHeadingRadians();
        double ex = getX() + Math.sin(abs) * d, ey = getY() + Math.cos(abs) * d;
        double px = ex, py = ey;
        for (int k = 1; k < 120; k++) {
            px += Math.sin(eh) * ev;
            py += Math.cos(eh) * ev;
            px = Math.max(18, Math.min(getBattleFieldWidth() - 18, px));
            py = Math.max(18, Math.min(getBattleFieldHeight() - 18, py));
            if (k * bs > Math.hypot(px - getX(), py - getY())) break;
        }
        double aim = Math.atan2(px - getX(), py - getY());
        setTurnGunRightRadians(Utils.normalRelativeAngle(aim - getGunHeadingRadians()));
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 4 && getEnergy() > p + 0.1) setFire(p);

        setTurnRadarRightRadians(Utils.normalRelativeAngle(abs - getRadarHeadingRadians()) * 2.0);
        lastEnergy = e.getEnergy();
    }

    public void onHitWall(HitWallEvent e) {
        dir = -dir;
    }
}
