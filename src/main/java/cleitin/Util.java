package cleitin;

/** Funcoes matematicas/fisicas do Robocode (angulos SEMPRE em radianos, 0 = norte, sentido horario). */
final class Util {
    static final double TWO_PI = 2 * Math.PI;
    static final double HALF_PI = Math.PI / 2;

    private Util() {}

    /** Normaliza para [-PI, PI). */
    static double normRel(double a) {
        a %= TWO_PI;
        if (a >= Math.PI) a -= TWO_PI;
        else if (a < -Math.PI) a += TWO_PI;
        return a;
    }

    static double lim(double lo, double v, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static double bulletSpeed(double power) {
        return 20 - 3 * power;
    }

    /** Dano causado por um tiro de potencia p. */
    static double bulletDamage(double p) {
        return 4 * p + (p > 1 ? 2 * (p - 1) : 0);
    }

    /** Menor potencia cujo dano e >= dmg. */
    static double powerForDamage(double dmg) {
        return dmg <= 4 ? dmg / 4 : (dmg + 2) / 6;
    }

    /** Maximum escape angle (radianos) de um alvo andando a 8 px/tick contra um tiro de velocidade bs. */
    static double mea(double bulletSpeed) {
        return Math.asin(8.0 / bulletSpeed);
    }

    /** Angulo absoluto (0 = norte, horario) de (x1,y1) para (x2,y2). */
    static double angle(double x1, double y1, double x2, double y2) {
        return Math.atan2(x2 - x1, y2 - y1);
    }

    static double dist(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Taxa maxima de giro do corpo (rad/tick) na velocidade v. */
    static double maxTurn(double v) {
        return Math.toRadians(10 - 0.75 * Math.abs(v));
    }

    /** Distancia do ponto (x,y) ate a borda do retangulo [minX,maxX]x[minY,maxY] andando na direcao a. */
    static double rayToWall(double x, double y, double a, double minX, double maxX, double minY, double maxY) {
        double dx = Math.sin(a), dy = Math.cos(a);
        double tx = Double.MAX_VALUE, ty = Double.MAX_VALUE;
        if (dx > 1e-9) tx = (maxX - x) / dx;
        else if (dx < -1e-9) tx = (minX - x) / dx;
        if (dy > 1e-9) ty = (maxY - y) / dy;
        else if (dy < -1e-9) ty = (minY - y) / dy;
        double t = Math.min(tx, ty);
        return t < 0 ? 0 : t;
    }
}
