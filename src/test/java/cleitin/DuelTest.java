package cleitin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import robocode.control.RobotSpecification;

/**
 * Batalhas reais (Robocode 1.11.1, sem interface grafica). Os limites sao propositalmente folgados em relacao
 * aos resultados medidos (veja docs/RELATORIO_DE_TESTES.md): o Robocode tem aleatoriedade e um teste que falha
 * por azar atrapalha mais do que ajuda. Eles detectam regressoes graves, nao pequenas variacoes de desempenho.
 */
class DuelTest {
    private static final String BOT = RobocodeArena.BOT;

    @Test
    void robotEDescobertoPeloRobocodeComODescritorCorreto() throws Exception {
        RobotSpecification spec = RobocodeArena.get().find(BOT);
        // robos carregados de pasta (nao de jar) aparecem com "*" no nome; versao e autor vem do .properties
        assertEquals(BOT + "*", spec.getName());
        assertEquals("1.4", spec.getVersion());
        assertEquals("Lucas", spec.getAuthorName());
        assertTrue(spec.getDescription().contains("wave surfing"), spec.getDescription());
    }

    @Test
    void vence_todosOsRoundsContraRoboParado() throws Exception {
        RobocodeArena.Result r = RobocodeArena.get().battle(10, 800, 600, BOT, "sample.SittingDuck");
        assertEquals(10, r.wins());
        assertTrue(r.scoreShare() > 95, "parcela da pontuacao: " + r.scoreShare());
    }

    @Test
    void vence_contraRoboQueAndaPelasParedes() throws Exception {
        RobocodeArena.Result r = RobocodeArena.get().battle(10, 800, 600, BOT, "sample.Walls");
        assertTrue(r.wins() >= 8, "vitorias: " + r.wins() + "/10");
    }

    @Test
    void vence_contraRoboImprevisivel() throws Exception {
        RobocodeArena.Result r = RobocodeArena.get().battle(10, 800, 600, BOT, "sample.Crazy");
        assertTrue(r.wins() >= 8, "vitorias: " + r.wins() + "/10");
    }

    @Test
    void vence_contraRoboAgressivoDeCurtaDistancia() throws Exception {
        RobocodeArena.Result r = RobocodeArena.get().battle(10, 800, 600, BOT, "sample.RamFire");
        assertTrue(r.wins() >= 8, "vitorias: " + r.wins() + "/10");
    }

    @Test
    void ganhaDoAdversarioGuessFactorEmMaioriaDosRounds() throws Exception {
        // GFBot: mira GuessFactor aprendida, o adversario de teste mais forte entre os simples
        RobocodeArena.Result r = RobocodeArena.get().battle(15, 800, 600, BOT, "tst.GFBot");
        assertTrue(r.wins() >= 10, "vitorias: " + r.wins() + "/15");
        assertTrue(r.scoreShare() > 60, "parcela da pontuacao: " + r.scoreShare());
    }

    @Test
    void sobreviveEmMeleeContraQuatroRobos() throws Exception {
        RobocodeArena.Result r = RobocodeArena.get().battle(5, 1000, 1000,
                BOT, "sample.Walls", "sample.Crazy", "sample.SpinBot", "sample.Tracker");
        assertTrue(r.wins() >= 3, "vitorias em melee: " + r.wins() + "/5");
    }
}
