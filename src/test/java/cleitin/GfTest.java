package cleitin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GfTest {
    @Test
    void extremosEMeioMapeiamParaOsBinsCertos() {
        assertEquals(0, Gf.bin(-1));
        assertEquals(Gf.MID, Gf.bin(0));
        assertEquals(Gf.BINS - 1, Gf.bin(1));
    }

    @Test
    void valoresForaDeMenosUmEUmSaoTruncados() {
        assertEquals(0, Gf.bin(-5));
        assertEquals(Gf.BINS - 1, Gf.bin(5));
    }

    @Test
    void gfDoBinEInversaDoBin() {
        for (int i = 0; i < Gf.BINS; i++) {
            assertEquals(i, Gf.bin(Gf.gfOfBin(i)), "bin " + i);
        }
        assertEquals(-1, Gf.gfOfBin(0), 1e-12);
        assertEquals(0, Gf.gfOfBin(Gf.MID), 1e-12);
        assertEquals(1, Gf.gfOfBin(Gf.BINS - 1), 1e-12);
    }

    @Test
    void addConcentraMassaAoRedorDoCentro() {
        double[] h = new double[Gf.BINS];
        Gf.add(h, 5, 1.0, 1.0);
        assertEquals(5, Gf.argmax(h));
        assertTrue(h[5] > h[4] && h[5] > h[6]);
        assertEquals(h[4], h[6], 1e-12); // nucleo simetrico
    }

    @Test
    void addComDecaimentoReduzOHistoricoAntigo() {
        double[] h = new double[Gf.BINS];
        Gf.add(h, 5, 1.0, 1.0);
        double antes = h[5];
        Gf.add(h, 25, 1.0, 0.5);
        // o pico antigo foi multiplicado por 0.5 (mais a cauda do novo nucleo, quase nula nessa distancia)
        assertEquals(antes * 0.5, h[5], 1e-3);
        assertEquals(25, Gf.argmax(h));
    }

    @Test
    void argmaxEmpateFavoreceOCentro() {
        double[] h = new double[Gf.BINS];
        java.util.Arrays.fill(h, 1.0);
        assertEquals(Gf.MID, Gf.argmax(h));
    }

    @Test
    void janelaSomaApenasOsBinsCobertos() {
        double[] h = new double[Gf.BINS];
        h[Gf.MID] = 2;
        h[Gf.MID + 1] = 3;
        h[Gf.BINS - 1] = 100;
        // janela estreita em torno de 0 pega so os bins do meio
        double s = Gf.window(h, 0, 0.07);
        assertTrue(s >= 2 && s < 100, "soma inesperada: " + s);
        // janela total soma tudo
        assertEquals(105, Gf.window(h, 0, 1.0), 1e-12);
    }
}
