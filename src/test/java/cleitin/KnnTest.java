package cleitin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KnnTest {
    private static double[] feat(double v) {
        double[] f = new double[Knn.F];
        java.util.Arrays.fill(f, v);
        return f;
    }

    @Test
    void semHistoricoSuficienteNaoPrevNada() {
        Knn knn = new Knn(100, 0.01);
        for (int i = 0; i < Config.KNN_MIN - 1; i++) knn.add(feat(0.5), 0.3);
        assertTrue(Double.isNaN(knn.query(feat(0.5), 5)));
        assertFalse(knn.density(feat(0.5), 5, new double[Gf.BINS]));
    }

    @Test
    void preveOGuessFactorDaSituacaoMaisParecida() {
        Knn knn = new Knn(500, 0.0);
        // duas situacoes distintas, cada uma com seu GF aprendido
        for (int i = 0; i < 30; i++) knn.add(feat(0.1), -0.8);
        for (int i = 0; i < 30; i++) knn.add(feat(0.9), 0.8);

        double perto01 = knn.query(feat(0.1), 8);
        double perto09 = knn.query(feat(0.9), 8);
        assertEquals(-0.8, perto01, 0.1);
        assertEquals(0.8, perto09, 0.1);
    }

    @Test
    void densidadeENormalizadaEConcentradaNoGfAprendido() {
        Knn knn = new Knn(500, 0.0);
        for (int i = 0; i < 40; i++) knn.add(feat(0.5), 0.4);
        double[] pdf = new double[Gf.BINS];
        assertTrue(knn.density(feat(0.5), 10, pdf));

        double soma = 0;
        for (double v : pdf) soma += v;
        assertEquals(1.0, soma, 1e-9);
        assertEquals(Gf.bin(0.4), Gf.argmax(pdf));
    }

    @Test
    void bufferCircularDescartaOMaisAntigo() {
        Knn knn = new Knn(10, 0.0);
        for (int i = 0; i < 25; i++) knn.add(feat(0.5), 0.0);
        assertEquals(10, knn.size());
    }
}
