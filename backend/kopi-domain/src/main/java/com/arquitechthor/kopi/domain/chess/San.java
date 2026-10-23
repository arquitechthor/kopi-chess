package com.arquitechthor.kopi.domain.chess;

import static com.arquitechthor.kopi.domain.chess.InvalidSanException.Problem.AMBIGUOUS;
import static com.arquitechthor.kopi.domain.chess.InvalidSanException.Problem.MALFORMED;
import static com.arquitechthor.kopi.domain.chess.InvalidSanException.Problem.NO_SUCH_MOVE;

import java.util.Optional;
import java.util.regex.Pattern;

/// Standard Algebraic Notation, the way moves are written in books and PGN:
/// `e4`, `Nf3`, `exd5`, `Nbd2`, `O-O`, `e8=Q`, `Qh4#`.
///
/// SAN only makes sense *in a position*: `Nf3` names the knight that can go
/// to `f3` right now, and whether `Nbd2` needs its `b` depends on where the
/// other knight is and whether it may legally move. So both directions take
/// the position.
///
/// The letters are the English ones (`N`, `B`, `R`, `Q`, `K`). Showing other
/// languages (`Cf3`) or figurines (`♘f3`) is the adapters' job (ADR-011).
public final class San {

    /// Piece letter, origin file, origin rank, capture, destination and
    /// promotion: `Nbd2`, `R1e2`, `exd5`, `e8=Q`. The `=` is optional, as
    /// many PGN files leave it out.
    private static final Pattern MOVE =
            Pattern.compile("([NBRQK])?([a-h])?([1-8])?x?([a-h][1-8])(?:=?([NBRQ]))?");

    /// What may follow a move without changing it: check and mate marks,
    /// annotations (`!`, `?!`…) and the old en passant suffix.
    private static final Pattern SUFFIX = Pattern.compile("(?:\\s*e\\.p\\.)?[+#!?]*$");

    private San() {}

    /// The SAN of `move`, which must be one of `position`'s legal moves.
    public static String format(Position position, Move move) {
        var san = new StringBuilder();
        if (move.kind() instanceof MoveKind.Castling(var side)) {
            san.append(side == CastleSide.KINGSIDE ? "O-O" : "O-O-O");
        } else {
            var piece = position.pieceAt(move.from()).orElseThrow().type();
            if (piece == PieceType.PAWN) {
                if (move.isCapture()) {
                    san.append(fileName(move.from())).append('x');
                }
                san.append(move.to());
            } else {
                san.append(Character.toUpperCase(piece.letter())).append(disambiguation(position, move, piece));
                if (move.isCapture()) {
                    san.append('x');
                }
                san.append(move.to());
            }
            if (move.kind() instanceof MoveKind.Promotion(var promoted, _)) {
                san.append('=').append(Character.toUpperCase(promoted.letter()));
            }
        }

        var after = position.apply(move);
        if (after.isInCheck()) {
            san.append(after.legalMoves().isEmpty() ? '#' : '+');
        }
        return san.toString();
    }

    /// What tells `move` apart from moves of other pieces of the same type to
    /// the same square: nothing if there are none, else the origin file if
    /// that is enough, else the rank, else both.
    ///
    /// Only *legal* moves count: a pinned knight that cannot go to `d2`
    /// does not force the other one to be written `Nbd2`.
    private static String disambiguation(Position position, Move move, PieceType piece) {
        var rivals = position.legalMoves().stream()
                .filter(other -> other.to().equals(move.to()) && !other.from().equals(move.from()))
                .filter(other -> position.pieceAt(other.from()).orElseThrow().type() == piece)
                .map(Move::from)
                .toList();
        if (rivals.isEmpty()) {
            return "";
        }
        var from = move.from();
        if (rivals.stream().noneMatch(square -> square.file() == from.file())) {
            return fileName(from);
        }
        if (rivals.stream().noneMatch(square -> square.rank() == from.rank())) {
            return String.valueOf(from.rank() + 1);
        }
        return from.toString();
    }

    /// The legal move of `position` that `san` stands for.
    ///
    /// Reading is a little more forgiving than writing, because SAN comes
    /// from people and from other programs: check marks and annotations are
    /// ignored, castling may use zeros (`0-0`), the `=` of a promotion and
    /// the `x` of a capture may be missing, and extra disambiguation
    /// (`Ngf3`) is fine. What it never does is guess: if the text fits more
    /// than one legal move, it is refused.
    ///
    /// @throws InvalidSanException with the [InvalidSanException.Problem] found
    public static Move parse(Position position, String san) {
        if (san == null) {
            throw new InvalidSanException(MALFORMED, "null", position);
        }
        var text = SUFFIX.matcher(san.strip()).replaceFirst("");

        var castling = switch (text) {
            case "O-O", "0-0" -> Optional.of(CastleSide.KINGSIDE);
            case "O-O-O", "0-0-0" -> Optional.of(CastleSide.QUEENSIDE);
            default -> Optional.<CastleSide>empty();
        };
        if (castling.isPresent()) {
            var side = castling.get();
            return position.legalMoves().stream()
                    .filter(move -> move.kind() instanceof MoveKind.Castling(var moveSide) && moveSide == side)
                    .findFirst()
                    .orElseThrow(() -> new InvalidSanException(NO_SUCH_MOVE, san, position));
        }

        var matcher = MOVE.matcher(text);
        if (!matcher.matches()) {
            throw new InvalidSanException(MALFORMED, san, position);
        }
        var piece = matcher.group(1) == null ? PieceType.PAWN : PieceType.fromLetter(matcher.group(1).charAt(0));
        var to = Square.parse(matcher.group(4));
        // A pawn without an origin file moves straight ahead: "d5" is never
        // a capture.
        var fromFile = matcher.group(2) != null ? matcher.group(2).charAt(0) - 'a'
                : piece == PieceType.PAWN ? to.file() : -1;
        var fromRank = matcher.group(3) != null ? matcher.group(3).charAt(0) - '1' : -1;
        var promotion = Optional.ofNullable(matcher.group(5)).map(letter -> PieceType.fromLetter(letter.charAt(0)));

        var candidates = position.legalMoves().stream()
                .filter(move -> move.to().equals(to))
                .filter(move -> !(move.kind() instanceof MoveKind.Castling))
                .filter(move -> position.pieceAt(move.from()).orElseThrow().type() == piece)
                .filter(move -> fromFile < 0 || move.from().file() == fromFile)
                .filter(move -> fromRank < 0 || move.from().rank() == fromRank)
                .filter(move -> promotionOf(move).equals(promotion))
                .toList();
        return switch (candidates.size()) {
            case 0 -> throw new InvalidSanException(NO_SUCH_MOVE, san, position);
            case 1 -> candidates.getFirst();
            default -> throw new InvalidSanException(AMBIGUOUS, san, position);
        };
    }

    /// The piece a move promotes to, if it is a promotion.
    private static Optional<PieceType> promotionOf(Move move) {
        return move.kind() instanceof MoveKind.Promotion(var piece, _) ? Optional.of(piece) : Optional.empty();
    }

    /// The file letter of a square: `e` for `e4`.
    private static String fileName(Square square) {
        return String.valueOf((char) ('a' + square.file()));
    }
}
