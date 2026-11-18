package cleitin;

/** Utilitarios de histogramas GuessFactor (BINS bins em [-1, 1], suavizados por nucleo gaussiano). */
final class Gf {
    static final int BINS = 31;
    static final int MID = (BINS - 1) / 2;
    static final double SIGMA = 1.8;
    /** Massa total de um nucleo centrado no meio (para normalizar). */
    static final double KMASS;
    private static final double[][] KERNEL = new double[BINS][BINS];

    static {
        for (int c = 0; c < BINS; c++) {
            for (int i = 0; i < BINS; i++) {
                double d = (i - c) / SIGMA;
                KERNEL[c][i] = Math.exp(-0.5 * d * d);
            }
        }
        double s = 0;
        for (int i = 0; i < BINS; i++) s += KERNEL[MID][i];
        KMASS = s;
    }

    private Gf() {}

    static int bin(double gf) {
        int b = (int) Math.round((Util.lim(-1, gf, 1) + 1) * 0.5 * (BINS - 1));
        return b < 0 ? 0 : (b >= BINS ? BINS - 1 : b);
    }

    static double gfOfBin(int i) {
        return i * 2.0 / (BINS - 1) - 1;
    }

    /** a = a*decay + w*kernel(center). */
    static void add(double[] a, int center, double w, double decay) {
        double[] k = KERNEL[center];
        for (int i = 0; i < BINS; i++) a[i] = a[i] * decay + w * k[i];
    }

    /** Soma dos bins cobertos por [gf-halfW, gf+halfW]. */
    static double window(double[] a, double gf, double halfW) {
        int lo = bin(gf - halfW), hi = bin(gf + halfW);
        double s = 0;
        for (int i = lo; i <= hi; i++) s += a[i];
        return s;
    }

    /** Indice do maior valor; empates resolvidos em favor do mais proximo do centro. */
    static int argmax(double[] a) {
        int best = MID;
        double bv = a[MID];
        for (int off = 1; off <= MID; off++) {
            int l = MID - off, r = MID + off;
            if (a[l] > bv) { bv = a[l]; best = l; }
            if (a[r] > bv) { bv = a[r]; best = r; }
        }
        return best;
    }
}
