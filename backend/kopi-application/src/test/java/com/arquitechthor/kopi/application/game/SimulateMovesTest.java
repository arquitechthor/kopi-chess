package com.arquitechthor.kopi.application.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/// The simulation mode: moves tried out from where a game stands, never
/// saved.
class SimulateMovesTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final GameId ID = new GameId(new UUID(0, 1));

    private final InMemoryGameRepository repository = new InMemoryGameRepository();
    private final SimulateMoves simulate = new SimulateMoves(repository);

    private Game storedGame(String... uci) {
        var game = Game.start(ID, Color.WHITE, BotProfile.PAWN, Position.initial(), NOW);
        for (var move : uci) {
            game = game.play(move, game.currentPosition().sideToMove(), NOW);
        }
        repository.insert(game);
        return game;
    }

    @Test
    void withoutMovesItIsWhereTheGameStands() {
        var game = storedGame("e2e4");

        var view = simulate.simulate(ID, List.of());

        assertThat(view.fen()).isEqualTo(game.currentPosition().toFen());
        assertThat(view.lastMove()).hasValue("e2e4");
        assertThat(view.moves()).isEmpty();
        assertThat(view.firstMove()).isEqualTo(1);
        assertThat(view.firstColor()).isEqualTo(Color.BLACK);
        assertThat(view.legalMoves()).hasSize(20).isSorted();
    }

    @Test
    void bothSidesMoveInTurnAndNothingIsSaved() {
        var game = storedGame("e2e4");

        var view = simulate.simulate(ID, List.of("e7e5", "g1f3", "b8c6"));

        assertThat(view.moves()).containsExactly("e5", "Nf3", "Nc6");
        assertThat(view.sideToMove()).isEqualTo(Color.WHITE);
        assertThat(view.lastMove()).hasValue("b8c6");
        var stored = repository.findById(ID).orElseThrow();
        assertThat(stored.moves()).hasSize(1);
        assertThat(stored.version()).isEqualTo(game.version());
    }

    @Test
    void itFindsMateWithoutEndingTheGame() {
        storedGame();

        var view = simulate.simulate(ID, List.of("f2f3", "e7e5", "g2g4", "d8h4"));

        assertThat(view.checkmate()).isTrue();
        assertThat(view.inCheck()).isTrue();
        assertThat(view.legalMoves()).isEmpty();
        assertThat(repository.findById(ID).orElseThrow().outcome()).isEmpty();
    }

    @Test
    void anIllegalMoveIsRefused() {
        storedGame();

        assertThatThrownBy(() -> simulate.simulate(ID, List.of("e2e4", "e2e4")))
                .isInstanceOf(IllegalSimulatedMoveException.class);
        assertThatThrownBy(() -> simulate.simulate(ID, Collections.nCopies(SimulateMovesUseCase.MAX_MOVES + 1, "e2e4")))
                .isInstanceOf(IllegalSimulatedMoveException.class);
    }

    @Test
    void anUnknownGameIsNotFound() {
        assertThatThrownBy(() -> simulate.simulate(ID, List.of())).isInstanceOf(GameNotFoundException.class);
    }
}
