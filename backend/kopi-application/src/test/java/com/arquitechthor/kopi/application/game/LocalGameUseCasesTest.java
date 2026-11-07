package com.arquitechthor.kopi.application.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.analysis.InMemoryAnalysisJobQueue;
import com.arquitechthor.kopi.application.port.BotRecord;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameMode;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import com.arquitechthor.kopi.domain.game.Player;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Two people taking turns at this computer (ADR-018), through the use
/// cases with in-memory ports.
class LocalGameUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private final InMemoryGameRepository repository = new InMemoryGameRepository();
    private final ScriptedBot bot = new ScriptedBot();
    private final InMemoryAnalysisJobQueue analyses = new InMemoryAnalysisJobQueue();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private int nextId = 1;

    private GameId newId() {
        return new GameId(new UUID(0, nextId++));
    }

    private GameView createLocal() {
        return new CreateLocalGame(repository, this::newId, clock).create(new CreateLocalGameCommand(" Ana ", "Luis"));
    }

    private GameView play(GameView game, String uci) {
        return new PlayMove(repository, bot, analyses, clock).play(new PlayMoveCommand(game.id(), uci, game.version()));
    }

    @Nested
    class Create {

        @Test
        void storesAGameBetweenTwoNamedPeople() {
            var game = createLocal();

            assertThat(game.mode()).isEqualTo(GameMode.LOCAL);
            assertThat(game.white()).hasValue(new Player.Local("Ana"));
            assertThat(game.black()).hasValue(new Player.Local("Luis"));
            assertThat(game.humanColor()).isEmpty();
            assertThat(game.botProfile()).isEmpty();
            assertThat(game.isHumanToMove()).isTrue();
            assertThat(repository.findById(game.id())).isPresent();
        }

        @Test
        void bothPeopleNeedAName() {
            assertThatThrownBy(() -> new CreateLocalGameCommand("Ana", "  "))
                    .isInstanceOf(InvalidPlayerNameException.class);
            assertThatThrownBy(() -> new CreateLocalGameCommand(null, "Luis"))
                    .isInstanceOf(InvalidPlayerNameException.class);
            assertThatThrownBy(() -> new CreateLocalGameCommand("x".repeat(31), "Luis"))
                    .isInstanceOf(InvalidPlayerNameException.class);
        }
    }

    @Nested
    class Play {

        @Test
        void eachPersonMovesOnTheirTurnAndNoBotReplies() {
            var game = play(createLocal(), "e2e4");
            assertThat(game.sideToMove()).isEqualTo(Color.BLACK);
            assertThat(game.isHumanToMove()).isTrue();

            game = play(game, "e7e5");
            assertThat(game.moves()).hasSize(2);
            assertThat(bot.calls).isZero();
        }

        @Test
        void aMateFinishesTheGameAndQueuesItsAnalysis() {
            var game = createLocal();
            for (var uci : new String[] {"f2f3", "e7e5", "g2g4", "d8h4"}) {
                game = play(game, uci);
            }

            assertThat(game.outcome()).hasValue(new GameOutcome.Checkmate(Color.BLACK));
            assertThat(game.isHumanToMove()).isFalse();
            assertThat(analyses.findByGameId(game.id())).isPresent();
        }

        @Test
        void theSideToMoveResigns() {
            var game = play(createLocal(), "e2e4");

            var resigned = new Resign(repository, analyses, clock).resign(new ResignCommand(game.id(), game.version()));

            assertThat(resigned.outcome()).hasValue(new GameOutcome.Resignation(Color.BLACK));
            assertThat(resigned.result()).hasValue(GameResult.WHITE_WIN);
        }
    }

    @Nested
    class AgreeToADraw {

        @Test
        void drawsTheGameAndQueuesItsAnalysis() {
            var game = play(createLocal(), "e2e4");

            var drawn = new AgreeDraw(repository, analyses, clock).agree(new AgreeDrawCommand(game.id(), game.version()));

            assertThat(drawn.status()).isEqualTo(GameStatus.FINISHED);
            assertThat(drawn.outcome()).hasValue(new GameOutcome.DrawByAgreement());
            assertThat(repository.findById(game.id()).orElseThrow().result()).hasValue(GameResult.DRAW);
            assertThat(analyses.findByGameId(game.id())).isPresent();
        }

        @Test
        void notAgainstABot() {
            var game = new CreateGame(repository, bot, LocalGameUseCasesTest.this::newId, clock)
                    .create(new CreateGameCommand(Color.WHITE, BotProfile.PAWN));

            assertThatThrownBy(() -> new AgreeDraw(repository, analyses, clock)
                    .agree(new AgreeDrawCommand(game.id(), game.version())))
                    .isInstanceOf(NotALocalGameException.class);
        }
    }

    @Test
    void localGamesDoNotCountInTheRecordAgainstTheBots() {
        createLocal();

        var records = BotRecord.tally(repository.findRecent(com.arquitechthor.kopi.application.port.GameFilter.ALL,
                0, 10).stream());

        assertThat(records.values()).allMatch(record -> record.played() == 0);
    }
}
