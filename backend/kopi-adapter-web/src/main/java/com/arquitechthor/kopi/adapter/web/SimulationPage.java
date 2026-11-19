package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.game.SimulationView;
import com.arquitechthor.kopi.domain.chess.Color;
import java.util.ArrayList;
import java.util.stream.Collectors;

/// What the simulation panel of the game page shows: the board's data for
/// the simulated position and the simulated moves as one line of text
/// (`12… ♘f6 13. ♗g5`). No chess rules here: everything comes from the
/// [SimulationView].
///
/// @param fen            the simulated position, for the board
/// @param turnColor      `white` or `black`
/// @param inCheck        highlights the king to move
/// @param legalMovesJson the legal moves in UCI as a JSON array
/// @param lastMove       the last move in UCI, or empty
/// @param line           the simulated moves, numbered; empty if none yet
/// @param checkmate      whether the simulation reached mate
/// @param stalemate      whether it reached stalemate
/// @param sideToMove     whose turn it is in the simulation
record SimulationPage(
        String fen,
        String turnColor,
        boolean inCheck,
        String legalMovesJson,
        String lastMove,
        String line,
        boolean checkmate,
        boolean stalemate,
        Color sideToMove) {

    static SimulationPage of(SimulationView view) {
        return new SimulationPage(
                view.fen(),
                GamePage.colorName(view.sideToMove()),
                view.inCheck(),
                view.legalMoves().stream().map(uci -> "\"" + uci + "\"").collect(Collectors.joining(",", "[", "]")),
                view.lastMove().orElse(""),
                line(view),
                view.checkmate(),
                view.stalemate(),
                view.sideToMove());
    }

    /// The moves numbered as in a score sheet: `5. e4 e5 6. ♘f3`, or
    /// `5… e5 6. ♘f3` when Black plays first.
    private static String line(SimulationView view) {
        var parts = new ArrayList<String>();
        var number = view.firstMove();
        var color = view.firstColor();
        for (int i = 0; i < view.moves().size(); i++) {
            var san = Figurines.of(view.moves().get(i));
            if (color == Color.WHITE) {
                parts.add(number + ". " + san);
            } else {
                parts.add(i == 0 ? number + "… " + san : san);
                number++;
            }
            color = color.opposite();
        }
        return String.join(" ", parts);
    }
}
