package cleitin;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;

import robocode.AdvancedRobot;
import robocode.BattleEndedEvent;
import robocode.Bullet;
import robocode.BulletHitBulletEvent;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.DeathEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.RobotDeathEvent;
import robocode.RoundEndedEvent;
import robocode.ScannedRobotEvent;
import robocode.WinEvent;

/**
 * Cleitin_O_BomDeGuerra - robo de combate para Robocode classico 1.11.1 (CombatController).
 *
 * Coordena: RadarSystem (aquisicao), MovementSystem (surf / risco minimo), GunSystem (miras virtuais + GuessFactor),
 * EnergyManager (potencia), EnemyModel (classe Enemy) e BattleStatistics.
 * Todo o raciocinio roda no laco principal (um passo por tick); os eventos apenas atualizam dados.
 */
public class Cleitin_O_BomDeGuerra extends AdvancedRobot {
    /** Conhecimento persistente entre rounds (a classe e carregada uma vez por batalha). */
    static final HashMap<String, Enemy> DB = new HashMap<String, Enemy>();
    static final BattleStatistics STATS = new BattleStatistics();

    private GunSystem gun;
    private MovementSystem move;
    private RadarSystem radar;
    private EnergyManager energy;
    private Enemy target;
    private boolean won;
    private int errors;
    private final ArrayList<Enemy> list = new ArrayList<Enemy>();

    @Override
    public void run() {
        setColors(new Color(20, 20, 30), new Color(220, 40, 40), new Color(250, 200, 40));
        setBulletColor(new Color(255, 230, 120));
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        gun = new GunSystem(this);
        move = new MovementSystem(this);
        radar = new RadarSystem(this);
        energy = new EnergyManager(this);
        won = false;
        target = null;
        for (Enemy e : DB.values()) e.newRound();
        gun.newRound();
        move.newRound();

        while (true) {
            try {
                long t0 = System.nanoTime();
                tick();
                STATS.onTick(System.nanoTime() - t0);
            } catch (RuntimeException ex) {
                if (errors++ < 3) out.println("ERRO tick: " + ex);
            }
            execute();
        }
    }

    private void tick() {
        long now = getTime();
        if (Double.isNaN(getX()) || Double.isNaN(getHeadingRadians())) return;   // guarda contra estado invalido
        move.recordState(now);

        list.clear();
        for (Enemy e : DB.values()) if (e.alive) list.add(e);
        int others = getOthers();

        target = chooseTarget(now, others);

        move.tick(target, list, others, now);

        double power = 0;
        if (target != null && target.lastSeen >= 0) {
            power = energy.firePower(target, Util.dist(getX(), getY(), target.x, target.y), others);
        }
        gun.tick(target, power, now);
        radar.tick(target, list, others, now);
    }

    // ---------------------------------------------------------------------------------------------
    // Selecao de alvo

    private Enemy chooseTarget(long now, int others) {
        Enemy best = null;
        double bs = -1e9;
        for (Enemy e : list) {
            if (e.lastSeen < 0) continue;
            if (others <= 1) return e;
            int age = (int) (now - e.lastSeen);
            double d = Util.dist(getX(), getY(), e.x, e.y);
            double s = 0;
            s += 1.2 * (1 - Math.min(d, 900) / 900);                       // proximidade
            s += 0.6 * (1 - Math.min(e.energy, 100) / 100);                // fragilidade
            if (e.energy < 16) s += 0.5;                                   // possivel finalizacao
            s += 1.5 * (e.bestGunRate() - 0.1);                            // facilidade provavel de acerto
            if (e.speedEma < 1.0) s += 0.25;                               // alvo quase parado
            s += 0.05 * Math.min(e.hitsOnUs, 6);                           // ameaca
            if (e == target) s += 0.25;                                    // histerese
            if (age > 12) s -= 0.04 * (age - 12);                          // dado velho
            if (s > bs) { bs = s; best = e; }
        }
        return best;
    }

    // ---------------------------------------------------------------------------------------------
    // Eventos

    @Override
    public void onScannedRobot(ScannedRobotEvent ev) {
        Enemy e = DB.get(ev.getName());
        if (e == null) {
            e = new Enemy(ev.getName());
            DB.put(ev.getName(), e);
        }
        e.update(getX(), getY(), getHeadingRadians(), ev.getBearingRadians(), ev.getDistance(),
                ev.getHeadingRadians(), ev.getVelocity(), ev.getEnergy(), getTime());
        move.onEnemyScan(e, getTime());
    }

    @Override
    public void onHitByBullet(HitByBulletEvent ev) {
        Enemy e = DB.get(ev.getName());
        Bullet b = ev.getBullet();
        STATS.onHitBy(b.getPower());
        if (e != null) {
            e.expectedGain += 3 * b.getPower();
            e.hitsOnUs++;
            e.dmgDealt += Util.bulletDamage(b.getPower());
            move.onHitByBullet(e, b, getTime());
        }
    }

    @Override
    public void onBulletHit(BulletHitEvent ev) {
        Enemy e = DB.get(ev.getName());
        double p = ev.getBullet().getPower();
        STATS.onBulletHit(p);
        if (e != null) {
            e.expectedDrop += Util.bulletDamage(p);
            e.hitsOnIt++;
            e.dmgTaken += Util.bulletDamage(p);
        }
    }

    @Override
    public void onBulletMissed(BulletMissedEvent ev) {
        STATS.onBulletMissed();
    }

    @Override
    public void onBulletHitBullet(BulletHitBulletEvent ev) {
        STATS.onBulletHitBullet();
    }

    @Override
    public void onHitRobot(HitRobotEvent ev) {
        STATS.onRobotCollision();
        Enemy e = DB.get(ev.getName());
        if (e != null) e.expectedDrop += 0.6;
        move.onWallOrRobot();
    }

    @Override
    public void onHitWall(HitWallEvent ev) {
        STATS.onWall();
        move.onWallOrRobot();
    }

    @Override
    public void onRobotDeath(RobotDeathEvent ev) {
        Enemy e = DB.get(ev.getName());
        if (e != null) {
            e.alive = false;
            gun.onEnemyDeath(e);
            move.onEnemyDeath(e);
            if (target == e) target = null;
        }
    }

    @Override
    public void onSkippedTurn(robocode.SkippedTurnEvent ev) {
        STATS.skipped++;
    }

    @Override
    public void onWin(WinEvent ev) {
        won = true;
    }

    @Override
    public void onDeath(DeathEvent ev) {
        won = false;
    }

    @Override
    public void onRoundEnded(RoundEndedEvent ev) {
        STATS.endRound(won || (getOthers() == 0 && getEnergy() > 0), getTime(), getEnergy(), out);
        if (Config.LOG && gun != null) out.println("GUNS " + gun.describeUse());
    }

    @Override
    public void onBattleEnded(BattleEndedEvent ev) {
        if (Config.LOG) {
            out.println("BATTLE wins=" + STATS.wins + "/" + STATS.rounds);
        }
    }
}
