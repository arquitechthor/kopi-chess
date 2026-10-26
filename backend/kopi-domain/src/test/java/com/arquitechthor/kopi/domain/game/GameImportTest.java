package com.arquitechthor.kopi.domain.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Pgn;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// `Game.imported`: a game from PGN, finished, with the board having the
/// last word on how it ended.
class GameImportTest {

    private static final GameId ID = new GameId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");

    private static Game imported(String pgn) {
        return Game.imported(ID, Pgn.parse(pgn), NOW);
    }

    private static InvalidImportException.Problem refusal(String pgn) {
        try {
            imported(pgn);
        } catch (InvalidImportException e) {
            return e.problem();
        }
        throw new AssertionError("Imported: " + pgn);
    }

    @Test
    void anImportedGameHasNoPersonNoBotAndArrivesFinished() {
        var game = imported("[White \"Morphy, Paul\"]\n[Black \"Duke Karl\"]\n\n1. e4 e5 2. Nf3 d6 1-0");

        assertThat(game.origin()).isEqualTo(GameOrigin.IMPORTED);
        assertThat(game.humanColor()).isEmpty();
        assertThat(game.botColor()).isEmpty();
        assertThat(game.botProfile()).isEmpty();
        assertThat(game.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.moves()).extracting(PlayedMove::san).containsExactly("e4", "e5", "Nf3", "d6");
        assertThat(game.moves()).allSatisfy(move -> assertThat(move.playedAt()).isEqualTo(NOW));
        assertThat(game.pgnTags()).containsEntry("White", "Morphy, Paul").containsEntry("Black", "Duke Karl");
        assertThat(game.version()).isZero();
        assertThat(game.createdAt()).isEqualTo(NOW);
        assertThat(game.finishedAt()).hasValue(NOW);
    }

    @Test
    void aMateOnTheBoardIsAMate() {
        var game = imported("1. f3 e5 2. g4 Qh4# 0-1");

        assertThat(game.outcome()).hasValue(new GameOutcome.Checkmate(Color.BLACK));
    }

    @Test
    void aMateOnTheBoardWithAnUnknownResultIsStillAMate() {
        assertThat(imported("1. f3 e5 2. g4 Qh4# *").outcome()).hasValue(new GameOutcome.Checkmate(Color.BLACK));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            // A resignation, a loss on time: the PGN knows, the board does not.
            "1. e4 e5 2. Nf3 Nc6 1-0, WHITE_WIN",
            "1. e4 e5 2. Nf3 Nc6 0-1, BLACK_WIN",
            // An agreed draw.
            "1. e4 e5 2. Nf3 Nc6 1/2-1/2, DRAW"})
    void otherwiseThePgnsResultStandsAsDeclared(String pgn, GameResult result) {
        var game = imported(pgn);

        assertThat(game.outcome()).hasValue(new GameOutcome.Declared(result));
        assertThat(game.result()).hasValue(result);
    }

    @Test
    void aDrawTheBoardAlsoShowsIsNamed() {
        // Bare kings at the end, recorded as a draw: insufficient material.
        var game = imported("[FEN \"4k3/8/8/8/8/8/3p4/4K3 w - - 0 1\"]\n\n1. Kxd2 1/2-1/2");

        assertThat(game.outcome()).hasValue(new GameOutcome.InsufficientMaterial());
    }

    @Test
    void repetitionsInTheMiddleDoNotEndAnImportedGame() {
        // Ke1 Ke8 three times over, then the game goes on: in real chess the
        // draw must be claimed, and nobody claimed it.
        var game = imported("1. e4 e5 2. Ke2 Ke7 3. Ke1 Ke8 4. Ke2 Ke7 5. Ke1 Ke8 6. Ke2 Ke7 7. Ke1 Ke8 "
                + "8. Nf3 Nc6 1-0");

        assertThat(game.moves()).hasSize(16);
        assertThat(game.outcome()).hasValue(new GameOutcome.Declared(GameResult.WHITE_WIN));
    }

    @Test
    void anUnfinishedGameIsRefused() {
        assertThat(refusal("1. e4 e5 2. Nf3 *")).isEqualTo(InvalidImportException.Problem.NOT_FINISHED);
    }

    @Test
    void aGameWithoutMovesIsRefused() {
        assertThat(refusal("[Event \"x\"]\n\n1-0")).isEqualTo(InvalidImportException.Problem.NO_MOVES);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            // Black mates, the PGN says White won.
            "1. f3 e5 2. g4 Qh4# 1-0",
            // A mate called a draw.
            "1. f3 e5 2. g4 Qh4# 1/2-1/2"})
    void aResultThatContradictsTheBoardIsRefused(String pgn) {
        assertThat(refusal(pgn)).isEqualTo(InvalidImportException.Problem.RESULT_CONTRADICTS_BOARD);
    }

    @Test
    void anImportedGameTakesNoMovesAndCannotBeResigned() {
        var game = imported("1. e4 e5 1-0");

        assertThatThrownBy(() -> game.play("g1f3", null, NOW)).isInstanceOf(GameNotInProgressException.class);
        assertThatThrownBy(() -> game.resign(null, NOW)).isInstanceOf(GameNotInProgressException.class);
    }
}
