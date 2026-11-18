package cleitin;

/** Politica de potencia de tiro e de gasto de energia. */
final class EnergyManager {
    private final Cleitin_O_BomDeGuerra r;

    EnergyManager(Cleitin_O_BomDeGuerra r) {
        this.r = r;
    }

    /**
     * Potencia de tiro para o alvo t a distancia dist (0 = nao atirar).
     * Equilibra dano, custo (a potencia sai da nossa energia), velocidade da bala
     * (menor potencia = bala mais rapida = mais chance de acerto) e a chance de acerto estimada.
     */
    double firePower(Enemy t, double dist, int others) {
        double e = r.getEnergy();
        if (e < 0.2) return 0;            // nunca nos desabilitamos atirando

        // Inimigo desabilitado (energia 0): qualquer acerto o destroi -> tiro minimo e barato.
        if (t.energy <= 0.0001) return Math.min(0.1, e - 0.1) < 0.1 ? 0 : 0.1;

        double p = (Config.POWER_RANGE_REF - dist) / Config.POWER_RANGE_DIV;

        // alvo quase parado e proximo: acerto quase certo -> investir em dano
        if (t.speedEma < 0.6 && t.lastSeen >= 0 && dist < 600) p = Math.max(p, 2.4);

        // estimativa de acerto vs. este alvo
        double q = t.bestGunRate();
        if (t.shotsAtIt >= 12) {
            if (q < 0.07) p *= 0.75;
            else if (q > 0.30) p *= 1.15;
        }

        if (others == 1 && t.shotsAtIt >= 25 && q < Config.HARD_Q) p = Math.min(p, Config.HARD_POWER_CAP);

        // melee: balas mais rapidas e baratas (mais alvos, menos certeza)
        if (others > 1) p = Math.min(p, Config.MELEE_POWER_CAP);

        // modo de sobrevivencia por energia
        double cap = 3.0;
        if (e < 50) cap = 2.4;
        if (e < 30) cap = 1.8;
        if (e < 15) cap = 1.2;
        if (e < 8) cap = 0.8;
        if (e < 4) cap = 0.4;
        // vantagem confortavel: libera potencia
        if (e > 1.8 * t.energy + 25 && others == 1) cap = 3.0;
        p = Math.min(p, cap);

        // finalizacao: potencia minima que mata (bala mais rapida, menos energia gasta)
        double kill = Util.powerForDamage(t.energy + 0.01);
        if (kill < p) p = Math.max(kill, 0.1);

        p = Util.lim(0.1, p, 3.0);
        p = Math.min(p, e - 0.1);
        return p < 0.1 ? 0 : p;
    }
}
