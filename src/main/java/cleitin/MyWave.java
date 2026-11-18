package cleitin;

/** Onda de um tiro NOSSO (real ou virtual), usada para aprender GuessFactors e pontuar miras. */
final class MyWave {
    double ox, oy, speed, power, refAngle, mea;
    int dir;
    long fireTime;
    Enemy target;
    final double[] angles = new double[GunSystem.N];
    int segA, segB, segC;
    double[] feat;
    boolean real;
}
