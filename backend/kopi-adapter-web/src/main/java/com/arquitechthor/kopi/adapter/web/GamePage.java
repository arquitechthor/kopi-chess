package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.MaterialBalance;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameOrigin;
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
/// @param material        who is ahead in material: a row per side that has
///                        pieces more than the other, the side at the top of
///                        the board first; empty when the material is level
/// @param legalMovesJson  the legal moves in UCI as a JSON array, for the board
/// @param lastMove        the last move in UCI, or empty
/// @param moveRows        the moves, one row per move number
/// @param botProfile      the opponent; `null` for an imported game
/// @param humanColor      the person's colour; `null` for an imported game
/// @param players         an imported game's players, `White – Black`, from
///                        its PGN (empty if it names neither); `null` for
///                        a game played here
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
        List<MaterialRow> material,
        String legalMovesJson,
        String lastMove,
        List<MoveRow> moveRows,
        BotProfile botProfile,
        Color humanColor,
        String players,
        boolean finished,
        String statusKey) {

    /// One side's lead in material: the opponent's pieces it is ahead by,
    /// as figurines (`♞♟`: what it has taken, net), and its points ahead,
    /// 0 unless it is the side ahead.
    record MaterialRow(Color color, String pieces, int points) {}

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
                // An imported game has no "you": it is seen from White's side.
                colorName(view.humanColor().orElse(Color.WHITE)),
                colorName(view.sideToMove()),
                view.isHumanToMove(),
                view.inCheck(),
                material(view.material(), view.humanColor().orElse(Color.WHITE)),
                legalMoves,
                view.lastMove().map(GameView.PlayedMoveView::uci).orElse(""),
                rows(view.moves()),
                view.botProfile().orElse(null),
                view.humanColor().orElse(null),
                view.origin() == GameOrigin.IMPORTED ? players(view) : null,
                view.status() == GameStatus.FINISHED,
                statusKey(view));
    }

    /// The rows of [#material], the side at the bottom of the board
    /// (`bottom`) last, as on chess sites.
    private static List<MaterialRow> material(MaterialBalance balance, Color bottom) {
        var rows = new ArrayList<MaterialRow>();
        for (var color : List.of(bottom.opposite(), bottom)) {
            var ahead = balance.ahead(color);
            if (!ahead.isEmpty()) {
                var pieces = ahead.stream().map(type -> Figurines.piece(color.opposite(), type))
                        .collect(Collectors.joining());
                rows.add(new MaterialRow(color, pieces, balance.pointsAhead(color)));
            }
        }
        return rows;
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

    static String statusKey(GameView view) {
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
                case GameOutcome.Declared(var result) -> "outcome.DECLARED." + result;
            };
        }
        if (view.isHumanToMove()) {
            return view.inCheck() ? "status.yourTurnInCheck" : "status.yourTurn";
        }
        return "status.botToMove";
    }

    /// An imported game's players (see [Players]).
    static String players(GameView view) {
        return Players.of(view.pgnTags());
    }

    private static String colorName(Color color) {
        return color.name().toLowerCase(Locale.ROOT);
    }
}
