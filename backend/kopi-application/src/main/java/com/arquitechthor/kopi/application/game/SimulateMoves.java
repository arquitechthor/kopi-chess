package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Rules;
import com.arquitechthor.kopi.domain.chess.San;
import com.arquitechthor.kopi.domain.game.GameId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// [SimulateMovesUseCase]: load the game and play the moves on its current
/// position, not on the game. Each move is looked up among the legal moves
/// of the position it is played in (ADR-004), so the client needs no chess
/// rules. Nothing is saved, so the game, its version and its analysis stay
/// as they were.
public final class SimulateMoves implements SimulateMovesUseCase {

    private final GameRepository repository;

    public SimulateMoves(GameRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public SimulationView simulate(GameId gameId, List<String> uciMoves) {
        Objects.requireNonNull(uciMoves, "uciMoves");
        if (uciMoves.size() > MAX_MOVES) {
            throw new IllegalSimulatedMoveException("More than " + MAX_MOVES + " simulated moves");
        }
        var game = Games.load(repository, gameId);
        var start = game.currentPosition();
        var position = start;
        var sans = new ArrayList<String>(uciMoves.size());
        var lastMove = game.moves().isEmpty() ? null : game.moves().getLast().move().toUci();
        for (var uci : uciMoves) {
            var current = position;
            var move = current.legalMoves().stream()
                    .filter(candidate -> candidate.toUci().equals(uci))
                    .findFirst()
                    .orElseThrow(() -> new IllegalSimulatedMoveException(
                            uci + " is not legal in " + current.toFen()));
            sans.add(San.format(current, move));
            position = current.apply(move);
            lastMove = uci;
        }
        var legalMoves = position.legalMoves().stream()
                .map(Move::toUci)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new SimulationView(position.toFen(), position.sideToMove(), position.isInCheck(),
                Rules.isCheckmate(position), Rules.isStalemate(position), Optional.ofNullable(lastMove),
                start.fullmoveNumber(), start.sideToMove(), sans, legalMoves);
    }
}
