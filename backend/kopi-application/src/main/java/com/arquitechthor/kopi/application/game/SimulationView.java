package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.chess.Color;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// A simulated position, with everything the board needs, computed by the
/// domain like a [GameView].
///
/// @param fen            the simulated position
/// @param sideToMove     whose turn it is there
/// @param inCheck        whether the side to move is in check
/// @param checkmate      whether it is mate
/// @param stalemate      whether it is stalemate
/// @param lastMove       the last simulated move in UCI, or the game's own
///                       last move before any
/// @param firstMove      the move number of the first simulated move
/// @param firstColor     who plays the first simulated move
/// @param moves          the simulated moves in SAN, in order
/// @param legalMoves     the legal moves there in UCI, sorted
public record SimulationView(
        String fen,
        Color sideToMove,
        boolean inCheck,
        boolean checkmate,
        boolean stalemate,
        Optional<String> lastMove,
        int firstMove,
        Color firstColor,
        List<String> moves,
        List<String> legalMoves) {

    public SimulationView {
        Objects.requireNonNull(fen, "fen");
        moves = List.copyOf(moves);
        legalMoves = List.copyOf(legalMoves);
    }
}
