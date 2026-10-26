package com.arquitechthor.kopi.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.AnalysisRepository;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.analysis.AnalysisSettings;
import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.analysis.GameAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveCategory;
import com.arquitechthor.kopi.domain.analysis.MoveClassifier.Classification;
import com.arquitechthor.kopi.domain.analysis.SearchLimits;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Opening;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/// The analysis repository against a real PostgreSQL, with the migrations
/// applied (the same throwaway database as [JdbcGameRepositoryIT]).
@SpringBootTest(classes = JdbcGameRepositoryIT.PersistenceTestApplication.class)
class JdbcAnalysisRepositoryIT {

    private static final Instant START = Instant.parse("2026-10-26T12:00:00Z");

    @Autowired
    AnalysisRepository analyses;

    @Autowired
    GameRepository games;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("delete from game").update();
    }

    /// A stored game where White played `uci` moves.
    private Game storedGame(String moves) {
        var game = Game.start(new GameId(UUID.randomUUID()), Color.WHITE, BotProfile.PAWN, Position.initial(), START);
        for (var uci : moves.split(" ")) {
            game = game.play(uci, game.currentPosition().sideToMove(), START.plusSeconds(game.moves().size() + 1L));
        }
        games.insert(game);
        return game;
    }

    /// 1.e4 e5 2.Qh5?? judged, with every kind of evaluation and a missed mate.
    private static GameAnalysis analysisOf(Game game) {
        var moves = List.of(
                new MoveAnalysis(1, new Evaluation.Centipawns(35), new Evaluation.Centipawns(30), "e2e4",
                        List.of("e4", "e5", "Nf3"), new Classification(MoveCategory.BOOK, 0.0092, OptionalInt.empty())),
                new MoveAnalysis(2, new Evaluation.Centipawns(30), new Evaluation.MateIn(2), "e7e5",
                        List.of("e5"), new Classification(MoveCategory.BLUNDER, 1.0552, OptionalInt.empty())),
                new MoveAnalysis(3, new Evaluation.MateIn(2), new Evaluation.Checkmate(Color.WHITE), "d1h5",
                        List.of("Qh5", "Nc6", "Qxf7#"),
                        new Classification(MoveCategory.MISSED_MATE, 0.0, OptionalInt.of(2))));
        return new GameAnalysis(game.id(), "Stockfish 19",
                new AnalysisSettings(new SearchLimits.Nodes(1_000_000), 2, 5), Color.WHITE,
                Optional.of(new Opening("C20", "King's Pawn Game")), moves, START, START.plusSeconds(90));
    }

    @Test
    void storesAndLoadsAnAnalysis() {
        var game = storedGame("e2e4 e7e5 d1h5");
        var analysis = analysisOf(game);

        analyses.save(analysis);

        assertThat(analyses.findByGameId(game.id())).hasValue(analysis);
    }

    @Test
    void keepsTheDepthOrTheNodesItWasSearchedWith() {
        var game = storedGame("e2e4 e7e5 d1h5");
        var byDepth = analysisOf(game);
        byDepth = new GameAnalysis(byDepth.gameId(), byDepth.engine(),
                new AnalysisSettings(new SearchLimits.Depth(16), 1, 3), byDepth.firstMover(), Optional.empty(),
                byDepth.moves(), byDepth.startedAt(), byDepth.completedAt());

        analyses.save(byDepth);

        assertThat(analyses.findByGameId(game.id())).hasValue(byDepth);
    }

    @Test
    void savingAgainReplacesTheEarlierAnalysis() {
        var game = storedGame("e2e4 e7e5 d1h5");
        analyses.save(analysisOf(game));
        var again = analysisOf(game);
        again = new GameAnalysis(again.gameId(), "Stockfish 20", again.settings(), again.firstMover(),
                again.opening(), again.moves(), again.startedAt(), again.completedAt());

        analyses.save(again);

        assertThat(analyses.findByGameId(game.id())).hasValue(again);
        assertThat(jdbc.sql("select count(*) from move_analysis").query(Integer.class).single()).isEqualTo(3);
    }

    @Test
    void aGameNotAnalysedHasNoAnalysis() {
        var game = storedGame("e2e4");

        assertThat(analyses.findByGameId(game.id())).isEmpty();
    }

    @Test
    void anAnalysisGoesWithItsGame() {
        var game = storedGame("e2e4 e7e5 d1h5");
        analyses.save(analysisOf(game));

        jdbc.sql("delete from game where id = :id").param("id", game.id().value()).update();

        assertThat(jdbc.sql("select count(*) from analysis").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from move_analysis").query(Integer.class).single()).isZero();
    }

    @Test
    void onlyMovesTheGamePlayedCanBeAnalysed() {
        var game = storedGame("e2e4");

        // The game has one move; the analysis claims three.
        assertThatThrownBy(() -> analyses.save(analysisOf(game)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(analyses.findByGameId(game.id())).isEmpty();
    }
}
