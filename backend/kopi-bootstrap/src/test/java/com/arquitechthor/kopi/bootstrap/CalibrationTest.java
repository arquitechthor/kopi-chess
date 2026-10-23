package com.arquitechthor.kopi.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.adapter.bots.KopiBots;
import com.arquitechthor.kopi.adapter.stockfish.StockfishBots;
import com.arquitechthor.kopi.adapter.stockfish.StockfishProperties;
import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.chess.Rules;
import com.arquitechthor.kopi.domain.chess.San;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/// Calibration (plan §7.3): each bot against the next one up, to check that
/// every level clearly beats the one below (more than 70% of the points).
///
/// Not part of any build: it takes tens of minutes and needs Stockfish.
///
/// ```
/// KOPI_STOCKFISH=/path/to/stockfish ./mvnw -pl kopi-bootstrap -am test -Pcalibration
/// ```
///
/// The bots play with the settings of `application.yml`, the ones the
/// application uses. Each pair plays every opening below twice, once with
/// each colour. The table goes to `target/calibration.md`.
@Tag("calibration")
class CalibrationTest {

    /// Varied, well-known starts, so the games do not all repeat.
    private static final List<String> OPENINGS = List.of(
            "e4 e5 Nf3 Nc6",       // open game
            "e4 c5 Nf3 d6",        // Sicilian
            "e4 e6 d4 d5",         // French
            "e4 c6 d4 d5",         // Caro-Kann
            "d4 d5 c4 e6",         // Queen's Gambit Declined
            "d4 Nf6 c4 g6",        // King's Indian
            "c4 e5 Nc3 Nf6",       // English
            "Nf3 d5 g3 Nf6");      // Réti

    /// A game longer than this is adjudicated a draw.
    private static final int MAX_PLIES = 300;

    /// The share of the points the stronger bot must take.
    private static final double REQUIRED = 0.70;

    record Result(BotProfile weaker, BotProfile stronger, int wins, int draws, int losses) {
        double score() {
            return (wins + draws / 2.0) / (wins + draws + losses);
        }
    }

    @Test
    void everyLevelClearlyBeatsTheOneBelow() throws IOException {
        var properties = stockfishProperties();
        try (var stockfish = StockfishBots.start(properties)) {
            var own = new KopiBots();
            BotPlayer bots = (position, profile) -> switch (profile) {
                case PAWN, KNIGHT -> own.chooseMove(position, profile);
                case BISHOP, ROOK, QUEEN -> stockfish.chooseMove(position, profile);
            };

            var results = new ArrayList<Result>();
            var levels = BotProfile.values();
            for (int i = 0; i + 1 < levels.length; i++) {
                var start = System.nanoTime();
                var result = match(bots, levels[i], levels[i + 1]);
                results.add(result);
                System.out.printf("%s vs %s: +%d =%d -%d (%.0f%%) in %s%n", result.stronger(), result.weaker(),
                        result.wins(), result.draws(), result.losses(), 100 * result.score(),
                        Duration.ofNanos(System.nanoTime() - start).withNanos(0));
            }
            var table = table(results, properties);
            Files.writeString(Path.of("target", "calibration.md"), table);
            System.out.println(table);

            assertThat(results).allSatisfy(result -> assertThat(result.score())
                    .as("%s against %s", result.stronger(), result.weaker())
                    .isGreaterThan(REQUIRED));
        }
    }

    /// Every opening twice, `stronger` with White and then with Black.
    private static Result match(BotPlayer bots, BotProfile weaker, BotProfile stronger) {
        int wins = 0;
        int draws = 0;
        int losses = 0;
        for (var opening : OPENINGS) {
            for (var strongerColor : Color.values()) {
                var winner = play(bots, opening, strongerColor == Color.WHITE ? stronger : weaker,
                        strongerColor == Color.WHITE ? weaker : stronger);
                if (winner == null) {
                    draws++;
                } else if (winner == strongerColor) {
                    wins++;
                } else {
                    losses++;
                }
            }
        }
        return new Result(weaker, stronger, wins, draws, losses);
    }

    /// The winning colour, or `null` for a draw.
    private static Color play(BotPlayer bots, String opening, BotProfile white, BotProfile black) {
        var position = Position.initial();
        for (var san : opening.split(" ")) {
            position = position.apply(San.parse(position, san));
        }
        var seen = new HashMap<Long, Integer>();
        for (int ply = 0; ply < MAX_PLIES; ply++) {
            if (Rules.isCheckmate(position)) {
                return position.sideToMove().opposite();
            }
            if (position.legalMoves().isEmpty() || Rules.isInsufficientMaterial(position)
                    || Rules.isFiftyMoveRule(position) || seen.merge(position.zobristHash(), 1, Integer::sum) >= 3) {
                return null;
            }
            var profile = position.sideToMove() == Color.WHITE ? white : black;
            position = position.apply(bots.chooseMove(position, profile));
        }
        return null;
    }

    /// `kopi.stockfish` from the application's own `application.yml`.
    private static StockfishProperties stockfishProperties() throws IOException {
        var environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment).bind("kopi.stockfish", StockfishProperties.class).get();
    }

    private static String table(List<Result> results, StockfishProperties properties) {
        var table = new StringBuilder("| Stronger | Weaker | + | = | − | Score |\n|---|---|---|---|---|---|\n");
        for (var result : results) {
            table.append("| %s | %s | %d | %d | %d | %.0f%% |%n".formatted(result.stronger(), result.weaker(),
                    result.wins(), result.draws(), result.losses(), 100 * result.score()));
        }
        table.append("\nStockfish settings: ").append(properties.profiles()).append('\n');
        return table.toString();
    }
}
