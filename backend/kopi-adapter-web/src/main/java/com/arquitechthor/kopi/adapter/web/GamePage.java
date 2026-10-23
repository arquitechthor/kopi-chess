package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/// What the game page shows, prepared for the template: the board's data
/// attributes, the move list in rows and the message key of the status
/// line. No chess rules here: everything comes from the [GameView].
///
/// @param id              the game, for URLs
/// @param version         sent back with every move or resignation
/// @param fen             the position for the board
/// @param orientation     `white` or `black`: the person's side at the bottom
/// @param turnColor       `white` or `black`
/// @param humanToMove     whether the board accepts a move
/// @param inCheck         highlights the king to move
/// @param legalMovesJson  the legal moves in UCI as a JSON array, for the board
/// @param lastMove        the last move in UCI, or empty
/// @param moveRows        the moves, one row per move number
/// @param botProfile      the opponent
/// @param humanColor      the person's colour
/// @param finished        whether the game is over
/// @param statusKey       message key of the status line (`status.yourTurn`,
///                        `outcome.CHECKMATE.BLACK`…)
record GamePage(
        String id,
        long version,
        String fen,
        String orientation,
        String turnColor,
        boolean humanToMove,
        boolean inCheck,
        String legalMovesJson,
        String lastMove,
        List<MoveRow> moveRows,
        BotProfile botProfile,
        Color humanColor,
        boolean finished,
        String statusKey) {

    /// One line of the move list: `1. e4 e5`. `black` is empty while White's
    /// move is the last one.
    record MoveRow(int number, String white, String black) {}

    static GamePage of(GameView view) {
        var legalMoves = view.legalMoves().stream()
                .map(move -> "\"" + move.uci() + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        return new GamePage(
                view.id().toString(),
                view.version(),
                view.fen(),
                colorName(view.humanColor()),
                colorName(view.sideToMove()),
                view.isHumanToMove(),
                view.inCheck(),
                legalMoves,
                view.lastMove().map(GameView.PlayedMoveView::uci).orElse(""),
                rows(view.moves()),
                view.botProfile(),
                view.humanColor(),
                view.status() == GameStatus.FINISHED,
                statusKey(view));
    }

    private static List<MoveRow> rows(List<GameView.PlayedMoveView> moves) {
        var rows = new ArrayList<MoveRow>();
        for (int i = 0; i < moves.size(); i += 2) {
            var white = Figurines.of(moves.get(i).san());
            var black = i + 1 < moves.size() ? Figurines.of(moves.get(i + 1).san()) : "";
            rows.add(new MoveRow(i / 2 + 1, white, black));
        }
        return rows;
    }

    private static String statusKey(GameView view) {
        if (view.outcome().isPresent()) {
            // Exhaustive over the sealed GameOutcome: every ending needs a
            // sentence in both languages.
            return switch (view.outcome().get()) {
                case GameOutcome.Checkmate(var winner) -> "outcome.CHECKMATE." + winner;
                case GameOutcome.Resignation(var resigned) -> "outcome.RESIGNATION." + resigned;
                case GameOutcome.Stalemate _ -> "outcome.STALEMATE";
                case GameOutcome.InsufficientMaterial _ -> "outcome.INSUFFICIENT_MATERIAL";
                case GameOutcome.ThreefoldRepetition _ -> "outcome.THREEFOLD_REPETITION";
                case GameOutcome.FiftyMoveRule _ -> "outcome.FIFTY_MOVE_RULE";
            };
        }
        if (view.isHumanToMove()) {
            return view.inCheck() ? "status.yourTurnInCheck" : "status.yourTurn";
        }
        return "status.botToMove";
    }

    private static String colorName(Color color) {
        return color.name().toLowerCase(Locale.ROOT);
    }
}
