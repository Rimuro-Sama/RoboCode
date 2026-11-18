package cleitin;

/**
 * Historico de situacoes (vetor de caracteristicas normalizadas -> GuessFactor), com pesos por amostra.
 * query(): GF de maior densidade entre as k situacoes mais parecidas (mira).
 * density(): densidade completa sobre os bins (usada pelo desvio de balas para imitar uma mira KNN inimiga).
 */
final class Knn {
    static final int F = 8;
    static final double[] W = {3.0, 1.4, 1.4, 2.0, 1.4, 1.0, 1.0, 0.8};

    private final int cap;
    private final double[][] feat;
    private final double[] gf, wt;
    private int head, count;
    private final double[] bd = new double[64];
    private final int[] bi = new int[64];
    private final double[] pdf = new double[Gf.BINS];

    private final double recency;

    Knn(int cap, double recency) {
        this.recency = recency;
        this.cap = cap;
        feat = new double[cap][];
        gf = new double[cap];
        wt = new double[cap];
    }

    int size() { return count; }

    void add(double[] f, double g) { add(f, g, 1.0); }

    void add(double[] f, double g, double w) {
        feat[head] = f;
        gf[head] = g;
        wt[head] = w;
        head = (head + 1) % cap;
        if (count < cap) count++;
    }

    private int select(double[] q, int k) {
        int n = count;
        k = Math.min(Math.min(k, n), bd.length);
        for (int i = 0; i < k; i++) { bd[i] = Double.MAX_VALUE; bi[i] = -1; }
        for (int i = 0; i < n; i++) {
            double[] f = feat[i];
            double d = 0;
            for (int j = 0; j < F; j++) {
                double x = (q[j] - f[j]) * W[j];
                d += x * x;
                if (d >= bd[k - 1]) break;
            }
            if (d < bd[k - 1]) {
                int p = k - 1;
                while (p > 0 && bd[p - 1] > d) { bd[p] = bd[p - 1]; bi[p] = bi[p - 1]; p--; }
                bd[p] = d;
                bi[p] = i;
            }
        }
        return k;
    }

    private void fill(int k, double[] out) {
        java.util.Arrays.fill(out, 0);
        for (int a = 0; a < k; a++) {
            if (bi[a] < 0) break;
            int age = (head - 1 - bi[a] + cap) % cap;
            double w = wt[bi[a]] / (1.0 + 6.0 * Math.sqrt(bd[a])) / (1.0 + age * recency);
            int bin = Gf.bin(gf[bi[a]]);
            for (int b = 0; b < Gf.BINS; b++) {
                double dd = b - bin;
                out[b] += w * Math.exp(-dd * dd / 5.0);
            }
        }
    }

    /**
     * Vizinhos de varias "janelas" de memoria aninhadas (as sizes[v] amostras mais recentes), k mais proximos de cada.
     * Preenche outGf/outW (peso 1/sqrt(distancia)) e devolve a quantidade. Tecnica inspirada nas visoes de memoria de
     * diferentes tamanhos usadas por miras anti-surfista (conceito; implementacao propria).
     */
    int multiView(double[] q, int[] sizes, int k, double[] outGf, double[] outW) {
        int nv = sizes.length;
        double[][] bestD = new double[nv][k];
        int[][] bestI = new int[nv][k];
        for (int v = 0; v < nv; v++) { java.util.Arrays.fill(bestD[v], Double.MAX_VALUE); java.util.Arrays.fill(bestI[v], -1); }
        for (int a = 0; a < count; a++) {
            int idx = (head - 1 - a + cap) % cap;
            double[] f = feat[idx];
            double d = 0;
            for (int j = 0; j < F; j++) {
                double x = (q[j] - f[j]) * W[j];
                d += x * x;
            }
            for (int v = 0; v < nv; v++) {
                if (a >= sizes[v]) continue;
                if (d < bestD[v][k - 1]) {
                    int p = k - 1;
                    while (p > 0 && bestD[v][p - 1] > d) { bestD[v][p] = bestD[v][p - 1]; bestI[v][p] = bestI[v][p - 1]; p--; }
                    bestD[v][p] = d;
                    bestI[v][p] = idx;
                }
            }
        }
        int n = 0;
        for (int v = 0; v < nv; v++) {
            for (int j = 0; j < k; j++) {
                if (bestI[v][j] < 0) break;
                outGf[n] = gf[bestI[v][j]];
                outW[n] = wt[bestI[v][j]] / Math.sqrt(Math.sqrt(bestD[v][j]) + 0.05);
                n++;
            }
        }
        return n;
    }

    /** GF previsto ou NaN se ha pouco historico. */
    double query(double[] q, int k) {
        if (count < Config.KNN_MIN) return Double.NaN;
        fill(select(q, k), pdf);
        return Gf.gfOfBin(Gf.argmax(pdf));
    }

    /** Preenche out com a densidade normalizada (soma 1). false se ha pouco historico. */
    boolean density(double[] q, int k, double[] out) {
        if (count < Config.SURF_KNN_MIN) return false;
        fill(select(q, k), out);
        double s = 0;
        for (int b = 0; b < Gf.BINS; b++) s += out[b];
        if (s < 1e-9) return false;
        for (int b = 0; b < Gf.BINS; b++) out[b] /= s;
        return true;
    }
}
