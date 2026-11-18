package cleitin;

import java.io.PrintStream;

/** Metricas de desempenho do nosso robo (tiros, acertos, dano, colisoes), por round e acumuladas. */
final class BattleStatistics {
    int shots, hits, misses, bulletCollisions;
    double dmgDealt, dmgTaken;
    int hitByCount, wallHits, robotHits;
    int enemyWaves;                 // tiros inimigos inferidos (estimativa)
    long tickNanos, tickMax, ticks; // custo de CPU do nosso laco
    int tEnemyWaves;
    int skipped;
    int hitsMatched, tHitsMatched;           // acertos sofridos associados a uma onda detectada

    // totais da batalha
    int tShots, tHits, tHitBy, tWall, tRobot, rounds, wins;
    double tDealt, tTaken;

    void onFire() { shots++; }
    void onTick(long ns) { tickNanos += ns; ticks++; if (ns > tickMax) tickMax = ns; }
    void onBulletHit(double power) { hits++; dmgDealt += Util.bulletDamage(power); }
    void onBulletMissed() { misses++; }
    void onBulletHitBullet() { bulletCollisions++; }
    void onHitBy(double power) { hitByCount++; dmgTaken += Util.bulletDamage(power); }
    void onWall() { wallHits++; }
    void onRobotCollision() { robotHits++; }

    void endRound(boolean won, long gameTicks, double energyLeft, PrintStream out) {
        rounds++;
        if (won) wins++;
        tEnemyWaves += enemyWaves;
        tHitsMatched += hitsMatched;
        tShots += shots; tHits += hits; tHitBy += hitByCount; tWall += wallHits; tRobot += robotHits;
        tDealt += dmgDealt; tTaken += dmgTaken;
        if (Config.LOG && out != null) {
            out.println("STAT round=" + rounds + " won=" + (won ? 1 : 0) + " ticks=" + gameTicks
                    + " energy=" + (int) energyLeft + " shots=" + shots + " hits=" + hits
                    + " acc=" + (shots == 0 ? 0 : Math.round(100.0 * hits / shots)) + "%"
                    + " dealt=" + (int) dmgDealt + " taken=" + (int) dmgTaken + " hitBy=" + hitByCount
                    + " wall=" + wallHits + " ram=" + robotHits + " skipped=" + skipped + " matched=" + hitsMatched + " eShots~" + enemyWaves + " cpu_us(avg/max)=" + (ticks == 0 ? 0 : tickNanos / ticks / 1000) + "/" + tickMax / 1000
                    + " | TOT wins=" + wins + "/" + rounds
                    + " acc=" + (tShots == 0 ? 0 : Math.round(100.0 * tHits / tShots)) + "%"
                    + " dealt=" + (int) tDealt + " taken=" + (int) tTaken + " enemyHit=" + (tHitBy) + "/" + (tEnemyWaves + enemyWaves) + " matched=" + tHitsMatched);
        }
        shots = hits = misses = bulletCollisions = hitByCount = wallHits = robotHits = 0;
        enemyWaves = 0; hitsMatched = 0; tickNanos = 0; tickMax = 0; ticks = 0;
        dmgDealt = dmgTaken = 0;
    }
}
