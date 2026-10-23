package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.PieceType;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.chess.Square;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

/// What the office-mode page shows (design §11.5): the board as a table of
/// letters, the legal moves grouped by piece and the history, all in the
/// visitor's SAN.
///
/// Reading the board from the FEN is presentation, not rules: which moves
/// are legal still comes from the [GameView].
///
/// @param id         the game, for URLs
/// @param version    sent back with every move
/// @param files      the column headers, left to right (`a`…`h`, or
///                   `h`…`a` for Black)
/// @param ranks      the rows, top to bottom
/// @param groups     the legal moves by piece, king first; empty when the
///                   person cannot move
/// @param history    the moves played, one row per move number
/// @param finished   whether the game is over
/// @param statusKey  message key of the status line (as on the board page)
/// @param letters    the piece letters for the legend: king, queen, rook,
///                   bishop, knight, pawn
record OfficePage(
        String id,
        long version,
        List<String> files,
        List<Rank> ranks,
        List<MoveGroup> groups,
        List<GamePage.MoveRow> history,
        boolean finished,
        boolean humanToMove,
        String statusKey,
        List<Character> letters) {

    /// A row of the board table.
    record Rank(String label, List<Cell> cells) {}

    /// A square: the letter of its piece (upper case for White, lower case
    /// for Black) or empty, and whether the last move touched it.
    record Cell(String square, String piece, boolean lastMove) {}

    /// The legal moves of one kind of piece.
    record MoveGroup(PieceType piece, char letter, List<String> moves) {}

    static OfficePage of(GameView view, OfficeNotation notation) {
        var position = Fen.parse(view.fen());
        var whiteAtBottom = view.humanColor() == Color.WHITE;
        var lastMove = view.lastMove()
                .map(move -> Set.of(move.uci().substring(0, 2), move.uci().substring(2, 4)))
                .orElse(Set.of());

        var files = new ArrayList<String>();
        for (int i = 0; i < 8; i++) {
            files.add(String.valueOf((char) ('a' + (whiteAtBottom ? i : 7 - i))));
        }
        var ranks = new ArrayList<Rank>();
        for (int row = 0; row < 8; row++) {
            var rank = whiteAtBottom ? 7 - row : row;
            var cells = new ArrayList<Cell>();
            for (int column = 0; column < 8; column++) {
                var square = Square.of(whiteAtBottom ? column : 7 - column, rank);
                cells.add(new Cell(square.toString(), letter(position, square, notation),
                        lastMove.contains(square.toString())));
            }
            ranks.add(new Rank(String.valueOf(rank + 1), cells));
        }

        var history = new ArrayList<GamePage.MoveRow>();
        var moves = view.moves();
        for (int i = 0; i < moves.size(); i += 2) {
            history.add(new GamePage.MoveRow(i / 2 + 1, notation.localise(moves.get(i).san()),
                    i + 1 < moves.size() ? notation.localise(moves.get(i + 1).san()) : ""));
        }

        return new OfficePage(view.id().toString(), view.version(), files, ranks,
                view.isHumanToMove() ? groups(view, position, notation) : List.of(), history,
                view.status() == GameStatus.FINISHED, view.isHumanToMove(), GamePage.statusKey(view),
                notation.pieceLetters());
    }

    private static String letter(Position position, Square square, OfficeNotation notation) {
        return position.pieceAt(square)
                .map(piece -> {
                    var letter = letterOf(piece.type(), notation);
                    return String.valueOf(piece.color() == Color.WHITE
                            ? Character.toUpperCase(letter) : Character.toLowerCase(letter));
                })
                .orElse("");
    }

    /// The legal moves grouped by the piece that moves, king first, each
    /// group in the order of the move list (UCI).
    private static List<MoveGroup> groups(GameView view, Position position, OfficeNotation notation) {
        var byPiece = new EnumMap<PieceType, List<String>>(PieceType.class);
        for (var move : view.legalMoves()) {
            var piece = position.pieceAt(Square.parse(move.uci().substring(0, 2))).orElseThrow().type();
            byPiece.computeIfAbsent(piece, _ -> new ArrayList<>()).add(notation.localise(move.san()));
        }
        var groups = new ArrayList<MoveGroup>();
        for (var piece : List.of(PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP,
                PieceType.KNIGHT, PieceType.PAWN)) {
            if (byPiece.containsKey(piece)) {
                groups.add(new MoveGroup(piece, letterOf(piece, notation), byPiece.get(piece)));
            }
        }
        return groups;
    }

    private static char letterOf(PieceType piece, OfficeNotation notation) {
        return switch (piece) {
            case KING -> notation.king();
            case QUEEN -> notation.queen();
            case ROOK -> notation.rook();
            case BISHOP -> notation.bishop();
            case KNIGHT -> notation.knight();
            case PAWN -> notation.pawn();
        };
    }
}
