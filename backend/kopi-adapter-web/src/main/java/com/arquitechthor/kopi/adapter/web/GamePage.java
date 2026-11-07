package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.MaterialBalance;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameMode;
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
/// @param mode            against a bot, two people here, online or imported
/// @param orientation     `white` or `black`: the person's side at the bottom;
///                        between two people here, the side to move, so
///                        the board turns after every move
/// @param turnColor       `white` or `black`
/// @param humanToMove     whether the board accepts a move
/// @param movableColor    `white` or `black`: the pieces the board lets the
///                        person move now; `null` if none
/// @param inCheck         highlights the king to move
/// @param material        who is ahead in material: a row per side that has
///                        pieces more than the other, the side at the top of
///                        the board first; empty when the material is level
/// @param legalMovesJson  the legal moves in UCI as a JSON array, for the board
/// @param lastMove        the last move in UCI, or empty
/// @param moveRows        the moves, one row per move number
/// @param botProfile      the bot; `null` if no bot plays
/// @param humanColor      the colour of the one person here; `null` for
///                        two people here and an imported game
/// @param players         the players of a game without a bot, `White –
///                        Black`: an imported game's, from its PGN (empty
///                        if it names neither), or the names of two people
///                        here; `null` against a bot
/// @param toMoveName      between two people here, the name of the one to
///                        move (for the status line and *Resign*); else
///                        `null`
/// @param finished        whether the game is over
/// @param rematchColor    for *Play again*: the other colour, against the same
///                        bot (`WHITE` or `BLACK`, as the home page form
///                        sends it); `null` while the game goes on and for a
///                        game without a bot
/// @param rematchWhite    for *Play again* between two people here: who
///                        takes White (they swap colours); else `null`
/// @param rematchBlack    who takes Black then
/// @param statusKey       message key of the status line (`status.yourTurn`,
///                        `outcome.CHECKMATE.BLACK`…)
/// @param statusArg       the argument of that message (the name of the
///                        person to move, between two people here), or
///                        `null`
record GamePage(
        String id,
        long version,
        GameMode mode,
        String fen,
        String orientation,
        String turnColor,
        boolean humanToMove,
        String movableColor,
        boolean inCheck,
        List<MaterialRow> material,
        String legalMovesJson,
        String lastMove,
        List<MoveRow> moveRows,
        BotProfile botProfile,
        Color humanColor,
        String players,
        String toMoveName,
        boolean finished,
        Color rematchColor,
        String rematchWhite,
        String rematchBlack,
        String statusKey,
        String statusArg) {

    /// One side's lead in material: the opponent's pieces it is ahead by,
    /// as figurines (`♞♟`: what it has taken, net), and its points ahead,
    /// 0 unless it is the side ahead.
    record MaterialRow(Color color, String pieces, int points) {}

    /// One line of the move list: `1. e4 e5`. `black` is empty while White's
    /// move is the last one.
    record MoveRow(int number, String white, String black) {}

    static GamePage of(GameView view) {
        var finished = view.status() == GameStatus.FINISHED;
        var local = view.mode() == GameMode.LOCAL;
        var legalMoves = view.legalMoves().stream()
                .map(move -> "\"" + move.uci() + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        var bottom = bottom(view);
        return new GamePage(
                view.id().toString(),
                view.version(),
                view.mode(),
                view.fen(),
                colorName(bottom),
                colorName(view.sideToMove()),
                view.isHumanToMove(),
                view.isHumanToMove() ? colorName(view.sideToMove()) : null,
                view.inCheck(),
                material(view.material(), bottom),
                legalMoves,
                view.lastMove().map(GameView.PlayedMoveView::uci).orElse(""),
                rows(view.moves()),
                view.botProfile().orElse(null),
                view.humanColor().orElse(null),
                players(view),
                local ? Players.name(view.player(view.sideToMove())) : null,
                finished,
                finished && view.mode() == GameMode.AGAINST_BOT
                        ? view.humanColor().map(Color::opposite).orElse(null) : null,
                finished && local ? Players.name(view.black()) : null,
                finished && local ? Players.name(view.white()) : null,
                statusKey(view),
                statusArg(view));
    }

    /// The side at the bottom of the board: the one person's colour against
    /// a bot or online; between two people here, the side to move, so each
    /// one plays from their side; an imported game is seen from White's.
    static Color bottom(GameView view) {
        return switch (view.mode()) {
            case AGAINST_BOT, ONLINE -> view.humanColor().orElse(Color.WHITE);
            case LOCAL -> view.sideToMove();
            case IMPORTED -> Color.WHITE;
        };
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
                case GameOutcome.DrawByAgreement _ -> "outcome.DRAW_AGREEMENT";
                case GameOutcome.Inactivity(var winner) -> "outcome.INACTIVITY." + winner;
            };
        }
        if (view.mode() == GameMode.LOCAL) {
            return view.inCheck() ? "status.localTurnInCheck" : "status.localTurn";
        }
        if (view.isHumanToMove()) {
            return view.inCheck() ? "status.yourTurnInCheck" : "status.yourTurn";
        }
        return "status.botToMove";
    }

    /// The argument of [#statusKey]'s message: the name of the person to
    /// move between two people here, `null` otherwise.
    static String statusArg(GameView view) {
        return view.mode() == GameMode.LOCAL && view.outcome().isEmpty()
                ? Players.name(view.player(view.sideToMove())) : null;
    }

    /// The players of a game without a bot (see [Players]): an imported
    /// game's from its PGN, two people here by their names; `null` against
    /// a bot.
    static String players(GameView view) {
        return switch (view.mode()) {
            case IMPORTED -> Players.of(view.pgnTags());
            case LOCAL, ONLINE -> Players.local(view.white(), view.black());
            case AGAINST_BOT -> null;
        };
    }

    private static String colorName(Color color) {
        return color.name().toLowerCase(Locale.ROOT);
    }
}
