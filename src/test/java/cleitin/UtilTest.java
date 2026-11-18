package cleitin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UtilTest {
    private static final double EPS = 1e-9;

    @Test
    void normRelMantemAnguloNoIntervalo() {
        assertEquals(0, Util.normRel(0), EPS);
        assertEquals(Math.PI / 2, Util.normRel(Math.PI / 2), EPS);
        // uma volta completa a mais nao muda o angulo
        assertEquals(0.5, Util.normRel(0.5 + 2 * Math.PI), EPS);
        assertEquals(-0.5, Util.normRel(-0.5 - 4 * Math.PI), EPS);
    }

    @Test
    void normRelResultadoSempreEntreMenosPiEPi() {
        for (double a = -50; a <= 50; a += 0.37) {
            double n = Util.normRel(a);
            assertTrue(n >= -Math.PI && n < Math.PI, "fora do intervalo para " + a + ": " + n);
            // equivale ao original modulo 2*PI
            assertEquals(Math.sin(a), Math.sin(n), 1e-9);
            assertEquals(Math.cos(a), Math.cos(n), 1e-9);
        }
    }

    @Test
    void limRestringeAoIntervalo() {
        assertEquals(1, Util.lim(1, 0, 5), EPS);
        assertEquals(5, Util.lim(1, 9, 5), EPS);
        assertEquals(3, Util.lim(1, 3, 5), EPS);
    }

    @Test
    void velocidadeDaBalaSegueARegraDoRobocode() {
        assertEquals(17, Util.bulletSpeed(1), EPS);   // 20 - 3*1
        assertEquals(11, Util.bulletSpeed(3), EPS);   // 20 - 3*3
    }

    @Test
    void danoDaBalaSegueARegraDoRobocode() {
        assertEquals(4, Util.bulletDamage(1), EPS);        // 4*p
        assertEquals(0.4, Util.bulletDamage(0.1), EPS);
        assertEquals(16, Util.bulletDamage(3), EPS);       // 4*3 + 2*(3-1)
    }

    @Test
    void powerForDamageEInversaDeBulletDamage() {
        for (double p = 0.1; p <= 3.0; p += 0.1) {
            assertEquals(p, Util.powerForDamage(Util.bulletDamage(p)), 1e-9, "p=" + p);
        }
    }

    @Test
    void anguloAbsolutoUsaNorteComoZeroESentidoHorario() {
        assertEquals(0, Util.angle(0, 0, 0, 10), EPS);                 // norte
        assertEquals(Math.PI / 2, Util.angle(0, 0, 10, 0), EPS);       // leste
        assertEquals(Math.PI, Math.abs(Util.angle(0, 0, 0, -10)), EPS); // sul
        assertEquals(-Math.PI / 2, Util.angle(0, 0, -10, 0), EPS);     // oeste
    }

    @Test
    void distanciaEuclidiana() {
        assertEquals(5, Util.dist(0, 0, 3, 4), EPS);
        assertEquals(0, Util.dist(7, 7, 7, 7), EPS);
    }

    @Test
    void maxEscapeAngleMaiorParaBalasMaisLentas() {
        assertTrue(Util.mea(11) > Util.mea(17));
        assertEquals(Math.asin(8.0 / 14), Util.mea(14), EPS);
    }

    @Test
    void taxaMaximaDeGiroDiminuiComAVelocidade() {
        assertEquals(Math.toRadians(10), Util.maxTurn(0), EPS);
        assertEquals(Math.toRadians(4), Util.maxTurn(8), EPS);
        assertEquals(Util.maxTurn(8), Util.maxTurn(-8), EPS); // so o modulo importa
    }

    @Test
    void raioAteAParede() {
        // arena 0..800 x 0..600; ponto (100, 100)
        assertEquals(500, Util.rayToWall(100, 100, 0, 0, 800, 0, 600), 1e-6);            // norte -> y=600
        assertEquals(700, Util.rayToWall(100, 100, Math.PI / 2, 0, 800, 0, 600), 1e-6);  // leste -> x=800
        assertEquals(100, Util.rayToWall(100, 100, Math.PI, 0, 800, 0, 600), 1e-6);      // sul -> y=0
        assertEquals(100, Util.rayToWall(100, 100, -Math.PI / 2, 0, 800, 0, 600), 1e-6); // oeste -> x=0
    }

    @Test
    void raioNuncaENegativo() {
        // ponto fora do retangulo andando para longe: resultado e truncado em 0
        assertEquals(0, Util.rayToWall(900, 100, Math.PI / 2, 0, 800, 0, 600), 1e-9);
    }
}
