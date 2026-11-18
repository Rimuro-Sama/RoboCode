package cleitin;

/** Onda de um tiro INIMIGO inferido por queda de energia (estimativa, nao confirmacao). */
final class EnemyWave {
    double ox, oy, speed, power, mea, directAngle;
    int dir;
    long fireTime;
    Enemy src;
    int ctx;
    double gfLinear;
    double weight = 1;      // chance de o tiro ser dirigido a nos (melee)
    double[] feat;          // situacao nossa no momento do tiro (para o desvio KNN)
    double[] pdf;           // densidade de GF prevista por uma mira KNN inimiga (ou null)
    double pdfConf;
    boolean crossed;
    double crossGf;

    double traveled(long now) {
        return (now - fireTime) * speed;
    }
}
