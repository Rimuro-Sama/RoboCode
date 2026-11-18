package cleitin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import robocode.BattleResults;
import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotSpecification;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;
import robocode.control.events.BattleErrorEvent;

/**
 * Roda batalhas reais do Robocode, sem interface grafica, a partir dos testes.
 *
 * Monta uma pasta temporaria do Robocode com o robo compilado (target/classes), os adversarios de teste
 * (target/test-classes/tst) e os robos de exemplo (sample.*, vindos do jar robocode.samples). O motor do
 * Robocode so admite uma batalha por vez e uma instancia por JVM, entao esta classe e um singleton e os
 * metodos sao sincronizados.
 */
final class RobocodeArena {
    static final String BOT = "cleitin.Cleitin_O_BomDeGuerra";

    private static RobocodeArena instance;

    private final Path home;
    private final RobocodeEngine engine;

    /** Resultado de um robo numa batalha. */
    static final class Score {
        final String name;
        final int score;
        final int firsts;

        Score(String name, int score, int firsts) {
            this.name = name;
            this.score = score;
            this.firsts = firsts;
        }
    }

    /** Resultado de uma batalha, na ordem em que os robos foram pedidos. */
    static final class Result {
        final int rounds;
        final List<Score> scores;

        Result(int rounds, List<Score> scores) {
            this.rounds = rounds;
            this.scores = scores;
        }

        /** Rounds em que o nosso robo (o primeiro da lista) ficou em 1o lugar. */
        int wins() {
            return scores.get(0).firsts;
        }

        /** Parcela da pontuacao total que ficou com o nosso robo, de 0 a 100. */
        double scoreShare() {
            double total = 0;
            for (Score s : scores) total += s.score;
            return total <= 0 ? 0 : 100.0 * scores.get(0).score / total;
        }
    }

    static synchronized RobocodeArena get() throws Exception {
        if (instance == null) instance = new RobocodeArena();
        return instance;
    }

    private RobocodeArena() throws Exception {
        home = Files.createTempDirectory("robocode-home");
        Path robots = Files.createDirectories(home.resolve("robots"));
        copyTree(codeDir(Util.class).resolve("cleitin"), robots.resolve("cleitin"));
        copyTree(codeDir(RobocodeArena.class).resolve("tst"), robots.resolve("tst"));
        extractSamples(robots);
        engine = new RobocodeEngine(home.toFile());
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    /** Descobre os robos pelo nome (ex.: "tst.GFBot") e confere se o descritor .properties foi encontrado. */
    synchronized RobotSpecification find(String name) {
        for (RobotSpecification s : engine.getLocalRepository()) {
            if (s.getClassName().equals(name)) return s;
        }
        throw new IllegalStateException("Robo nao encontrado no repositorio do Robocode: " + name);
    }

    /** Roda uma batalha; o primeiro robo da lista e o "nosso". Lanca excecao se o motor reportar erro. */
    synchronized Result battle(int rounds, int width, int height, String... robotNames) {
        RobotSpecification[] specs = new RobotSpecification[robotNames.length];
        for (int i = 0; i < specs.length; i++) specs[i] = find(robotNames[i]);

        final BattleResults[][] holder = new BattleResults[1][];
        final List<String> errors = new ArrayList<String>();
        BattleAdaptor listener = new BattleAdaptor() {
            @Override
            public void onBattleCompleted(BattleCompletedEvent e) {
                holder[0] = e.getIndexedResults();
            }

            @Override
            public void onBattleError(BattleErrorEvent e) {
                errors.add(e.getError());
            }
        };
        engine.addBattleListener(listener);
        try {
            engine.runBattle(new BattleSpecification(rounds, new BattlefieldSpecification(width, height), specs), true);
        } finally {
            engine.removeBattleListener(listener);
        }
        if (!errors.isEmpty()) throw new IllegalStateException("Erro de batalha: " + errors);
        if (holder[0] == null) throw new IllegalStateException("A batalha nao terminou");

        List<Score> scores = new ArrayList<Score>();
        for (int i = 0; i < specs.length; i++) {
            BattleResults r = holder[0][i];
            scores.add(new Score(specs[i].getName(), r == null ? 0 : r.getScore(), r == null ? 0 : r.getFirsts()));
        }
        return new Result(rounds, scores);
    }

    private void shutdown() {
        try {
            engine.close();
        } catch (RuntimeException ignored) {
            // encerrando a JVM
        }
        try {
            deleteTree(home);
        } catch (IOException ignored) {
            // pasta temporaria: o sistema limpa depois
        }
    }

    // ---- utilitarios de arquivo ----

    private static Path codeDir(Class<?> c) throws URISyntaxException {
        return new File(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath();
    }

    private static void extractSamples(Path robots) throws Exception {
        Class<?> sample = Class.forName("sample.Walls");
        File jar = new File(sample.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().startsWith("sample/")) continue;
                Path out = robots.resolve(e.getName());
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(e)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyTree(final Path from, final Path to) throws IOException {
        if (!Files.isDirectory(from)) throw new IOException("Pasta nao encontrada (rode 'mvn test'): " + from);
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
