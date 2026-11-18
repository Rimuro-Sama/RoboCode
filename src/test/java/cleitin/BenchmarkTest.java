package cleitin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Bateria longa de confrontos, fora da execucao normal dos testes.
 *
 *   mvn test -Pbenchmark                       (20 rounds por confronto)
 *   mvn test -Pbenchmark -Dbenchmark.rounds=35 (35 rounds, como no RoboRumble)
 *
 * Nao faz afirmacoes de desempenho: so grava os resultados em target/benchmark/resultados.md e .csv
 * (e falha se o motor do Robocode reportar erro). Os numeros variam entre execucoes por causa da
 * aleatoriedade do Robocode e dependem da maquina.
 */
@Tag("benchmark")
class BenchmarkTest {
    private static final String BOT = RobocodeArena.BOT;

    /** {adversario, descricao}. Duelos 1v1 em arena 800x600. */
    private static final String[][] DUELS = {
        {"sample.SittingDuck", "parado"},
        {"tst.Circler", "movimento circular"},
        {"sample.Walls", "anda pelas paredes"},
        {"sample.SpinBot", "girando, agressivo"},
        {"sample.Crazy", "imprevisivel"},
        {"tst.Strafer", "imprevisivel + mira linear"},
        {"tst.GFBot", "mira GuessFactor"},
        {"tst.Camper", "defensivo, canto"},
        {"sample.RamFire", "agressivo, curta distancia"},
        {"sample.Tracker", "seguidor"},
        {"sample.TrackFire", "seguidor + tiro"},
        {"sample.Fire", "parado, gira"},
        {"sample.Corners", "cantos"},
        {"sample.VelociRobot", "velocidade"},
        {"tst.SurfGF", "FORTE: wave surfing + GuessFactor"},
        {"tst.Sniper", "FORTE: longa distancia"},
        {"tst.Charger", "FORTE: agressivo, potencia 3"},
        {"tst.DropDodger", "esquiva por queda de energia"},
        {"tst.GravBot", "anti-gravidade"},
    };

    @Test
    void executaABateriaEGravaOsResultados() throws Exception {
        int rounds = Integer.getInteger("benchmark.rounds", 20);
        RobocodeArena arena = RobocodeArena.get();

        List<String[]> rows = new ArrayList<String[]>();
        for (String[] d : DUELS) {
            RobocodeArena.Result r = arena.battle(rounds, 800, 600, BOT, d[0]);
            rows.add(new String[] {"duelo 1v1 800x600", d[0], d[1], String.valueOf(r.wins()), String.valueOf(rounds),
                String.format(Locale.ROOT, "%.1f", r.scoreShare())});
        }

        RobocodeArena.Result melee5 = arena.battle(rounds, 1000, 1000,
                BOT, "sample.Walls", "sample.Crazy", "sample.SpinBot", "sample.Tracker");
        rows.add(new String[] {"melee 5 robos 1000x1000", "Walls+Crazy+SpinBot+Tracker", "mistura", String.valueOf(melee5.wins()),
            String.valueOf(rounds), String.format(Locale.ROOT, "%.1f", melee5.scoreShare())});

        RobocodeArena.Result meleeFortes = arena.battle(rounds, 1000, 1000,
                BOT, "tst.SurfGF", "tst.Sniper", "tst.Charger", "tst.DropDodger", "tst.GravBot");
        rows.add(new String[] {"melee 6 robos 1000x1000", "SurfGF+Sniper+Charger+DropDodger+GravBot", "os fortes",
            String.valueOf(meleeFortes.wins()), String.valueOf(rounds), String.format(Locale.ROOT, "%.1f", meleeFortes.scoreShare())});

        write(rows, rounds);
    }

    private static void write(List<String[]> rows, int rounds) throws IOException {
        File dir = new File(System.getProperty("benchmark.output", "target/benchmark"));
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Nao foi possivel criar " + dir);

        PrintWriter md = new PrintWriter(new File(dir, "resultados.md"), StandardCharsets.UTF_8.name());
        PrintWriter csv = new PrintWriter(new File(dir, "resultados.csv"), StandardCharsets.UTF_8.name());
        try {
            md.println("## Benchmark: Cleitin-O-BomDeGuerra 1.4 (Robocode 1.11.1)");
            md.println();
            md.println(rounds + " rounds por confronto. Os numeros variam entre execucoes (aleatoriedade do Robocode) e dependem da maquina.");
            md.println();
            md.println("| Cenario | Adversario(s) | Perfil | 1o lugar | % da pontuacao |");
            md.println("|---|---|---|---|---|");
            csv.println("cenario,adversarios,perfil,vitorias,rounds,percentual_pontuacao");
            for (String[] r : rows) {
                md.println("| " + r[0] + " | `" + r[1] + "` | " + r[2] + " | " + r[3] + "/" + r[4] + " | " + r[5] + " |");
                csv.println(r[0] + "," + r[1] + "," + r[2].replace(',', ';') + "," + r[3] + "," + r[4] + "," + r[5]);
            }
        } finally {
            md.close();
            csv.close();
        }
        System.out.println("Benchmark gravado em " + dir.getAbsolutePath());
    }
}
