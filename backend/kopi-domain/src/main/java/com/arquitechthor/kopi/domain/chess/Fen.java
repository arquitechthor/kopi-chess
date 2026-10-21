package com.arquitechthor.kopi.domain.chess;

import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_CASTLING_RIGHTS;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_EN_PASSANT_SQUARE;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_FULLMOVE_NUMBER;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_HALFMOVE_CLOCK;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_PIECE_PLACEMENT;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_SIDE_TO_MOVE;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.MISSING_KING;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.PAWN_ON_BACK_RANK;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.TOO_MANY_KINGS;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.WRONG_NUMBER_OF_FIELDS;

import java.util.HashMap;
import java.util.Optional;
import java.util.regex.Pattern;

/// Forsyth–Edwards Notation: one line of text that describes a position.
///
/// ```
/// rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1
/// └──────────── piece placement ───────────┘ │  │    │  │ └ fullmove number
///                                  side to move ┘  │    │  └ halfmove clock
///                                    castling rights ┘    └ en passant target
/// ```
///
/// [#parse] is strict about syntax but accepts the common four-field form
/// (without the two clocks), defaulting to `0 1`.
public final class Fen {

    /// The standard starting position.
    public static final String INITIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final Pattern FIELD_SEPARATOR = Pattern.compile("\\s+");
    private static final Pattern CASTLING = Pattern.compile("-|K?Q?k?q?");

    private Fen() {
    }

    /// Reads a position from FEN.
    ///
    /// @throws InvalidFenException with the [InvalidFenException.Problem] found
    public static Position parse(String fen) {
        if (fen == null || fen.isBlank()) {
            throw new InvalidFenException(WRONG_NUMBER_OF_FIELDS, String.valueOf(fen), "empty");
        }
        var fields = FIELD_SEPARATOR.split(fen.strip());
        if (fields.length != 4 && fields.length != 6) {
            throw new InvalidFenException(WRONG_NUMBER_OF_FIELDS, fen,
                    "expected 6 fields (or 4 without clocks), found " + fields.length);
        }

        var board = parsePiecePlacement(fields[0], fen);
        var sideToMove = parseSideToMove(fields[1], fen);
        var castlingRights = parseCastlingRights(fields[2], fen);
        var enPassantTarget = parseEnPassantTarget(fields[3], sideToMove, fen);
        int halfmoveClock = fields.length == 6 ? parseNumber(fields[4], 0, INVALID_HALFMOVE_CLOCK, fen) : 0;
        int fullmoveNumber = fields.length == 6 ? parseNumber(fields[5], 1, INVALID_FULLMOVE_NUMBER, fen) : 1;

        return new Position(board, sideToMove, castlingRights, enPassantTarget, halfmoveClock, fullmoveNumber);
    }

    /// Writes a position as FEN, always with all six fields.
    public static String format(Position position) {
        return String.join(" ",
                formatPiecePlacement(position.board()),
                position.sideToMove() == Color.WHITE ? "w" : "b",
                position.castlingRights().toFen(),
                position.enPassantTarget().map(Square::toString).orElse("-"),
                Integer.toString(position.halfmoveClock()),
                Integer.toString(position.fullmoveNumber()));
    }

    // --- Piece placement ---

    private static Board parsePiecePlacement(String placement, String fen) {
        var ranks = placement.split("/", -1);
        if (ranks.length != 8) {
            throw new InvalidFenException(INVALID_PIECE_PLACEMENT, fen, "expected 8 ranks, found " + ranks.length);
        }
        var pieces = new HashMap<Square, Piece>();
        for (int i = 0; i < 8; i++) {
            int rank = 7 - i; // FEN lists rank 8 first
            int file = 0;
            for (char c : ranks[i].toCharArray()) {
                if (c >= '1' && c <= '8') {
                    file += c - '0';
                } else {
                    if (file > 7) {
                        throw new InvalidFenException(INVALID_PIECE_PLACEMENT, fen, "rank " + (rank + 1) + " is too long");
                    }
                    try {
                        pieces.put(Square.of(file, rank), Piece.fromFenChar(c));
                    } catch (IllegalArgumentException _) {
                        throw new InvalidFenException(INVALID_PIECE_PLACEMENT, fen, "unknown piece '" + c + "'");
                    }
                    file++;
                }
            }
            if (file != 8) {
                throw new InvalidFenException(INVALID_PIECE_PLACEMENT, fen,
                        "rank " + (rank + 1) + " has " + file + " squares instead of 8");
            }
        }
        checkStructure(pieces, fen);
        return Board.of(pieces);
    }

    /// One king per side, no pawns on the first or last rank.
    private static void checkStructure(HashMap<Square, Piece> pieces, String fen) {
        for (var color : Color.values()) {
            long kings = pieces.values().stream().filter(p -> p.equals(new Piece(color, PieceType.KING))).count();
            if (kings == 0) {
                throw new InvalidFenException(MISSING_KING, fen, "no " + color + " king");
            }
            if (kings > 1) {
                throw new InvalidFenException(TOO_MANY_KINGS, fen, kings + " " + color + " kings");
            }
        }
        pieces.forEach((square, piece) -> {
            if (piece.type() == PieceType.PAWN && (square.rank() == 0 || square.rank() == 7)) {
                throw new InvalidFenException(PAWN_ON_BACK_RANK, fen, "pawn on " + square);
            }
        });
    }

    private static String formatPiecePlacement(Board board) {
        var placement = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                var piece = board.pieceAt(Square.of(file, rank));
                if (piece.isEmpty()) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    placement.append(empty);
                    empty = 0;
                }
                placement.append(piece.get().fenChar());
            }
            if (empty > 0) {
                placement.append(empty);
            }
            if (rank > 0) {
                placement.append('/');
            }
        }
        return placement.toString();
    }

    // --- The other fields ---

    private static Color parseSideToMove(String field, String fen) {
        return switch (field) {
            case "w" -> Color.WHITE;
            case "b" -> Color.BLACK;
            default -> throw new InvalidFenException(INVALID_SIDE_TO_MOVE, fen, "expected 'w' or 'b', found '" + field + "'");
        };
    }

    private static CastlingRights parseCastlingRights(String field, String fen) {
        if (field.isEmpty() || !CASTLING.matcher(field).matches()) {
            throw new InvalidFenException(INVALID_CASTLING_RIGHTS, fen,
                    "expected '-' or some of 'KQkq' in that order, found '" + field + "'");
        }
        return new CastlingRights(field.contains("K"), field.contains("Q"), field.contains("k"), field.contains("q"));
    }

    private static Optional<Square> parseEnPassantTarget(String field, Color sideToMove, String fen) {
        if (field.equals("-")) {
            return Optional.empty();
        }
        Square square;
        try {
            square = Square.parse(field);
        } catch (IllegalArgumentException _) {
            throw new InvalidFenException(INVALID_EN_PASSANT_SQUARE, fen, "not a square: '" + field + "'");
        }
        // The target is the square the pawn skipped: on rank 6 if Black just
        // pushed (White to move), on rank 3 if White just pushed.
        int expectedRank = sideToMove == Color.WHITE ? 5 : 2;
        if (square.rank() != expectedRank) {
            throw new InvalidFenException(INVALID_EN_PASSANT_SQUARE, fen,
                    square + " cannot be an en passant target with " + sideToMove + " to move");
        }
        return Optional.of(square);
    }

    private static int parseNumber(String field, int minimum, InvalidFenException.Problem problem, String fen) {
        try {
            int number = Integer.parseInt(field);
            if (number >= minimum) {
                return number;
            }
        } catch (NumberFormatException _) {
            // fall through to the exception below
        }
        throw new InvalidFenException(problem, fen, "expected a number >= " + minimum + ", found '" + field + "'");
    }
}
